package com.halloweenhunt;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** 술래잡기(괴물사냥꾼) 게임 진행 */
public final class HuntManager {
    // ── 조정용 상수 (tick = 1/20초) ──
    public static final int TOTAL_TICKS = 5 * 60 * 20;      // 제한시간 5분
    public static final int LATE_TICKS = 4 * 60 * 20;       // 경과 4분 → 랜턴 사용 가능 + 술래 더 빨라짐
    public static final double SPEED_BASE = 0.15;           // 술래 기본 이속 +15%
    public static final double SPEED_LATE = 0.35;           // 4분 이후 +35%
    public static final int LANTERN_GLOW_TICKS = 10 * 20;   // 랜턴: 몬스터 발광 10초
    public static final int LANTERN_COOLDOWN = 30 * 20;     // 랜턴 쿨타임 30초
    public static final int DASH_COOLDOWN = 10 * 20;        // 대쉬 쿨타임 10초

    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(HalloweenHunt.MODID, "hunter_speed");
    private static final Random RANDOM = new Random();
    private static final String MANGTAE_PLAYER_NAME = "BackBUGI624";

    private static final Set<UUID> pastHunters = new HashSet<>();
    private static final Set<UUID> monsters = new HashSet<>();
    private static final Set<UUID> caught = new HashSet<>();
    private static final List<Runnable> later = new ArrayList<>();

    private static ServerBossEvent bar;
    private static boolean running;
    private static int elapsed;
    private static UUID hunter;
    private static UUID mangtae;

    private HuntManager() {}

    // ───────────── 상태 조회 ─────────────
    public static boolean isRunning() { return running; }
    public static boolean isHunter(Player p) { return running && p.getUUID().equals(hunter); }
    public static boolean isMangtae(Player p) { return running && p.getUUID().equals(mangtae); }
    public static boolean isParticipant(Player p) { return running && (p.getUUID().equals(hunter) || monsters.contains(p.getUUID())); }
    public static boolean isMonsterAlive(Player p) { return running && monsters.contains(p.getUUID()) && !caught.contains(p.getUUID()); }

    /** 다음 서버 틱에 실행 */
    public static void later(Runnable r) { later.add(r); }

    public static void resetPastHunters() { pastHunters.clear(); }

    // ───────────── 시작 / 종료 ─────────────
    /** @return 실패 사유, 성공이면 null */
    public static String start(MinecraftServer server) {
        if (running) return "이미 게임이 진행 중입니다.";
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        if (all.size() < 2) return "최소 2명이 필요합니다.";

        // 망태할아버지는 지정한 플레이어에게만 배정한다.
        ServerPlayer m = null;
        for (ServerPlayer p : all) {
            if (MANGTAE_PLAYER_NAME.equalsIgnoreCase(p.getGameProfile().getName())) {
                m = p;
                break;
            }
        }
        if (m == null) return MANGTAE_PLAYER_NAME + " 플레이어가 접속해야 시작할 수 있습니다.";

        // 고정 역할은 술래 후보 및 술래 순환 기록에서 제외한다.
        List<ServerPlayer> eligibleHunters = new ArrayList<>(all);
        eligibleHunters.remove(m);
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer p : eligibleHunters) {
            if (!pastHunters.contains(p.getUUID())) candidates.add(p);
        }
        if (candidates.isEmpty()) {
            pastHunters.clear();
            candidates.addAll(eligibleHunters);
            broadcast(server, Component.literal("§7술래 후보가 모두 술래를 해봤으므로 기록을 초기화합니다."));
        }
        ServerPlayer h = candidates.get(RANDOM.nextInt(candidates.size()));
        pastHunters.add(h.getUUID());

        hunter = h.getUUID();
        mangtae = m.getUUID();
        monsters.clear();
        caught.clear();
        SackManager.reset();
        elapsed = 0;

        bar = new ServerBossEvent(Component.literal("남은 시간 5:00"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        for (ServerPlayer p : all) {
            prepare(p);
            bar.addPlayer(p);
            if (p != h) monsters.add(p.getUUID());
        }
        HuntItems.giveHunterKit(h);
        applySpeed(h, SPEED_BASE);
        HuntItems.giveMangtaeKit(m);

        running = true;

        title(h, Component.literal("§c괴물사냥꾼"), Component.literal("5분 안에 몬스터를 모두 잡아라!"));
        for (ServerPlayer p : all) {
            if (p == h) continue;
            if (p == m) title(p, Component.literal("§6망태할아버지"), Component.literal("몬스터를 망태기에 숨겨라!"));
            else title(p, Component.literal("§a몬스터"), Component.literal("5분 동안 살아남아라!"));
        }
        broadcast(server, Component.literal("§6[할로윈] §f술래: §c" + h.getName().getString() + " §f/ 몬스터 " + monsters.size() + "명"));
        return null;
    }

    public static void stop(MinecraftServer server, Component reason) {
        if (!running) return;
        running = false;
        SackManager.releaseAll(server);
        if (bar != null) bar.removeAllPlayers();

        Set<UUID> everyone = new HashSet<>(monsters);
        if (hunter != null) everyone.add(hunter);
        for (UUID id : everyone) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null) a.removeModifier(SPEED_ID);
            p.removeAllEffects();
            p.getInventory().clearContent();
            p.setGameMode(GameType.SURVIVAL);
        }
        monsters.clear();
        caught.clear();
        hunter = null;
        mangtae = null;
        broadcast(server, reason);
    }

    // ───────────── 매 틱 ─────────────
    public static void tick(MinecraftServer server) {
        if (!later.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(later);
            later.clear();
            tasks.forEach(Runnable::run);
        }
        if (!running) return;

        elapsed++;
        SackManager.tick(server);

        float progress = Math.max(0f, 1f - (float) elapsed / TOTAL_TICKS);
        bar.setProgress(progress);
        if (elapsed % 20 == 0) {
            int sec = Math.max(0, (TOTAL_TICKS - elapsed) / 20);
            bar.setName(Component.literal(String.format("남은 시간 %d:%02d  |  남은 몬스터 %d", sec / 60, sec % 60, monsters.size() - caught.size())));
        }

        ServerPlayer h = server.getPlayerList().getPlayer(hunter);
        if (h == null) {
            stop(server, Component.literal("§c술래가 나가서 게임이 종료되었습니다."));
            return;
        }

        if (elapsed == LATE_TICKS) {
            applySpeed(h, SPEED_LATE);
            title(h, Component.literal("§e랜턴 사용 가능!"), Component.literal("이동속도가 더 빨라졌다"));
            broadcast(server, Component.literal("§c[알림] 술래가 더 빨라졌다! 랜턴이 해금되었다!"));
        }

        if (caught.size() >= monsters.size()) {
            stop(server, Component.literal("§c§l술래 승리! §f몬스터를 모두 잡았다."));
        } else if (elapsed >= TOTAL_TICKS) {
            stop(server, Component.literal("§a§l몬스터 승리! §f생존자 " + (monsters.size() - caught.size()) + "명"));
        }
    }

    // ───────────── 이벤트 훅 ─────────────
    public static void catchPlayer(ServerPlayer p) {
        if (!isMonsterAlive(p)) return;
        caught.add(p.getUUID());
        SackManager.releaseCarrier(p);
        p.setHealth(p.getMaxHealth());
        p.setGameMode(GameType.SPECTATOR);
        broadcast(p.server, Component.literal("§c" + p.getName().getString() + " 잡힘! §7(남은 몬스터 " + (monsters.size() - caught.size()) + "명)"));
    }

    public static void onLogout(ServerPlayer p) {
        if (!running) return;
        if (p.getUUID().equals(hunter)) {
            stop(p.server, Component.literal("§c술래가 나가서 게임이 종료되었습니다."));
        } else if (monsters.contains(p.getUUID())) {
            SackManager.releaseCarrier(p);
            SackManager.release(p.server, p.getUUID());
            caught.add(p.getUUID());
        }
    }

    /** 막대기로 술래를 때렸을 때: 내구도 1 소모(=사용 횟수), 다 쓰면 다음 틱에 제거 */
    public static void onStickHit(ServerPlayer attacker, ItemStack stick) {
        int dmg = stick.getDamageValue() + 1;
        stick.setDamageValue(dmg);
        if (dmg >= stick.getMaxDamage()) {
            later(() -> {
                if (HuntItems.is(attacker.getMainHandItem(), HuntItems.STICK)) {
                    attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 1f);
                }
            });
        }
    }

    // ───────────── 술래 스킬 ─────────────
    public static void useLantern(ServerPlayer p) {
        if (!isHunter(p) || p.getCooldowns().isOnCooldown(Items.LANTERN)) return;
        if (elapsed < LATE_TICKS) {
            int left = (LATE_TICKS - elapsed + 19) / 20;
            p.displayClientMessage(Component.literal("§c랜턴은 4분부터 사용 가능 (" + left + "초 남음)"), true);
            return;
        }
        for (UUID id : monsters) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(id);
            if (m == null || caught.contains(id) || SackManager.isSacked(m)) continue; // 망태기 속은 안 보임
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, LANTERN_GLOW_TICKS, 0, false, false, false));
        }
        p.getCooldowns().addCooldown(Items.LANTERN, LANTERN_COOLDOWN);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2f, 0.8f);
        p.displayClientMessage(Component.literal("§e랜턴! 몬스터들이 빛난다!"), true);
    }

    public static void dash(ServerPlayer p) {
        if (!isHunter(p) || p.getCooldowns().isOnCooldown(Items.FEATHER)) return;
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        p.setDeltaMovement(dir.x * 1.8, 0.2, dir.z * 1.8);
        p.hurtMarked = true;
        p.resetFallDistance();
        p.getCooldowns().addCooldown(Items.FEATHER, DASH_COOLDOWN);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1f, 1.5f);
    }

    // ───────────── 유틸 ─────────────
    private static void prepare(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20f);
    }

    private static void applySpeed(ServerPlayer p, double amount) {
        AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(SPEED_ID);
        a.addTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void title(ServerPlayer p, Component main, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(main));
    }

    public static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}

