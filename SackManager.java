package com.halloweenhunt;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 망태기(가두기/탈출) 로직.
 * 갇힌 플레이어는 망태할아버지에게 탑승 상태라서, 클라이언트가 매 틱 서버로 보내는
 * "입력 패킷"의 점프 값을 서버에서 읽는다. → 클라이언트용 별도 패킷/키 처리 불필요.
 */
public final class SackManager {
    public static final int CAPTURE_COOLDOWN = 2 * 60 * 20; // 망태기 쿨타임 2분
    public static final int ESCAPE_WINDOW = 3 * 20;         // 3초
    public static final int ESCAPE_PRESSES = 15;            // 3초 안에 눌러야 하는 횟수

    private static final class Occupant {
        final UUID carrier;
        final boolean returnHunterToSpawn;
        int windowStart = -1;
        int count = 0;
        boolean lastJump = false;
        Occupant(UUID carrier, boolean returnHunterToSpawn) {
            this.carrier = carrier;
            this.returnHunterToSpawn = returnHunterToSpawn;
        }
    }

    private static final Map<UUID, Occupant> byVictim = new HashMap<>();
    private static final Map<UUID, UUID> victimByCarrier = new HashMap<>();
    private static final Map<UUID, Integer> nextCapture = new HashMap<>();

    private static Field JUMPING;
    static {
        Field f = null;
        try {
            // 배포(난독화) 환경: SRG 이름으로 조회. 개발 환경도 자동 변환됨.
            f = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20899_"); // LivingEntity.jumping
        } catch (Throwable t1) {
            try {
                f = LivingEntity.class.getDeclaredField("jumping");
                f.setAccessible(true);
            } catch (Throwable t2) {
                f = null;
            }
        }
        JUMPING = f;
    }

    private SackManager() {}

    private static boolean isJumping(ServerPlayer p) {
        try {
            return JUMPING != null && JUMPING.getBoolean(p);
        } catch (Exception ex) {
            return false;
        }
    }

    public static void reset() {
        byVictim.clear();
        victimByCarrier.clear();
        nextCapture.clear();
    }

    public static boolean isSacked(ServerPlayer p) {
        return byVictim.containsKey(p.getUUID());
    }

    /** 망태 밧줄을 플레이어에게 우클릭 */
    public static void capture(ServerPlayer carrier, ServerPlayer target) {
        if (!MonsterRoles.canAct(carrier) || HuntManager.isHeadStart() || !HuntManager.isMangtae(carrier) || !HuntManager.isMonsterAlive(carrier)) return;

        // 이미 누가 들어있으면 → 다시 쓰면 풀어준다
        if (victimByCarrier.containsKey(carrier.getUUID())) {
            releaseCarrier(carrier);
            return;
        }
        if (!carrier.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.MANGTAE_SACK.get())) {
            carrier.displayClientMessage(Component.literal("§c망태기를 상체에 착용해야 합니다."), true);
            return;
        }
        if (target == carrier || isSacked(target)) return;
        if (!HuntManager.isHunter(target) && !HuntManager.isMonsterAlive(target)) return;

        int now = carrier.server.getTickCount();
        int next = nextCapture.getOrDefault(carrier.getUUID(), 0);
        if (now < next) {
            carrier.displayClientMessage(Component.literal("§c망태기 쿨타임 " + ((next - now + 19) / 20) + "초"), true);
            return;
        }
        if (!target.startRiding(carrier, true)) return;
        nextCapture.put(carrier.getUUID(), now + CAPTURE_COOLDOWN);

        Occupant o = new Occupant(carrier.getUUID(), HuntManager.isHunter(target));
        byVictim.put(target.getUUID(), o);
        victimByCarrier.put(carrier.getUUID(), target.getUUID());

        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));

        carrier.level().playSound(null, carrier.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
        target.displayClientMessage(Component.literal("§e망태기에 갇혔다! 스페이스바를 3초 안에 " + ESCAPE_PRESSES + "번 연타!"), true);
        carrier.displayClientMessage(Component.literal("§a" + target.getName().getString() + " 을(를) 망태기에 넣었다! (밧줄을 다시 쓰면 꺼냄)"), true);
    }

    /** 스페이스를 "누른 순간" 1회 */
    private static void press(ServerPlayer v, Occupant o, int now) {
        if (o.windowStart < 0 || now - o.windowStart > ESCAPE_WINDOW) {
            o.windowStart = now;
            o.count = 0;
        }
        o.count++;
        if (o.count >= ESCAPE_PRESSES) {
            ServerPlayer c = v.server.getPlayerList().getPlayer(o.carrier);
            if (c != null) c.displayClientMessage(Component.literal("§c" + v.getName().getString() + " 이(가) 망태기에서 탈출했다!"), true);
            release(v.server, v.getUUID());
            return;
        }
        int left = Math.max(0, (ESCAPE_WINDOW - (now - o.windowStart) + 19) / 20);
        v.displayClientMessage(Component.literal("§e탈출 " + o.count + "/" + ESCAPE_PRESSES + "  (" + left + "초)"), true);
    }

    public static void tick(MinecraftServer server) {
        if (byVictim.isEmpty()) return;
        int now = server.getTickCount();
        for (Map.Entry<UUID, Occupant> en : new ArrayList<>(byVictim.entrySet())) {
            Occupant o = en.getValue();
            ServerPlayer v = server.getPlayerList().getPlayer(en.getKey());
            ServerPlayer c = server.getPlayerList().getPlayer(o.carrier);
            if (v == null || c == null || !HuntManager.isMonsterAlive(c)) {
                release(server, en.getKey());
                continue;
            }
            if (v.getVehicle() != c) v.startRiding(c, true); // 쉬프트 등으로 내려도 다시 태움

            if (o.windowStart >= 0 && now - o.windowStart > ESCAPE_WINDOW) {
                o.windowStart = -1;
                o.count = 0;
                v.displayClientMessage(Component.literal("§c시간 초과! 다시 연타하세요!"), true);
            }
            boolean jump = isJumping(v);
            if (jump && !o.lastJump) press(v, o, now);
            o.lastJump = jump;
        }
    }

    public static void releaseCarrier(ServerPlayer carrier) {
        UUID victim = victimByCarrier.get(carrier.getUUID());
        if (victim != null) release(carrier.server, victim);
    }

    public static void releaseAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(byVictim.keySet())) release(server, id);
    }

    public static void release(MinecraftServer server, UUID victimId) {
        Occupant o = byVictim.remove(victimId);
        if (o == null) return;
        victimByCarrier.remove(o.carrier);
        ServerPlayer v = server.getPlayerList().getPlayer(victimId);
        if (v != null) {
            v.stopRiding();
            v.removeEffect(MobEffects.INVISIBILITY);
            // Capture-time role is retained even if the game ends before release.
            if (o.returnHunterToSpawn) {
                ServerLevel spawnLevel = server.overworld();
                BlockPos spawn = spawnLevel.getSharedSpawnPos();
                v.teleportTo(spawnLevel, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        spawnLevel.getSharedSpawnAngle(), 0.0F);
                v.setDeltaMovement(0.0, 0.0, 0.0);
                v.resetFallDistance();
                v.displayClientMessage(Component.literal("§e망태기에서 나와 스폰으로 돌아왔습니다!"), true);
            } else {
                v.displayClientMessage(Component.literal("§a망태기에서 나왔다!"), true);
            }
        }
    }
}



