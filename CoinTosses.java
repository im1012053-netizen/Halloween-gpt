package com.halloweenhunt;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** One server decision per toss; cosmetic testing works outside a match for every player. */
public final class CoinTosses {
    private record Toss(int endsAt, boolean heads, boolean contract) {}
    private static final Map<UUID, Toss> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private CoinTosses() {}

    public static void start(ServerPlayer player) {
        if (!player.getMainHandItem().is(ModItems.COIN.get()) || player.isSpectator() || !player.isAlive()) return;
        int now = player.server.getTickCount();
        if (ACTIVE.containsKey(player.getUUID())) return;
        if (HuntManager.isParticipant(player) && !MonsterRoles.canAct(player)) return;
        boolean contract = MonsterRoles.isJack(player) && HuntManager.isMonsterAlive(player) && !HuntManager.isHeadStart();
        if (contract && COOLDOWNS.getOrDefault(player.getUUID(), 0) > now) {
            player.displayClientMessage(Component.literal("§e계약 쿨타임 " + ((COOLDOWNS.get(player.getUUID()) - now + 19) / 20) + "초"), true);
            return;
        }
        boolean heads = player.getRandom().nextBoolean();
        ACTIVE.put(player.getUUID(), new Toss(now + CoinMotion.DURATION_TICKS, heads, contract));
        CoinNetwork.send(player, heads);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5F, 1.8F);
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID,Toss>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) { it.remove(); continue; }
            Toss toss = entry.getValue();
            if (now < toss.endsAt()) continue;
            it.remove();
            p.displayClientMessage(Component.literal(toss.heads() ? "§6동전: 앞면!" : "§7동전: 뒷면!"), true);
            if (!toss.contract() || !MonsterRoles.isJack(p) || !HuntManager.isMonsterAlive(p)
                    || !MonsterRoles.canAct(p) || HuntManager.isHeadStart()) continue;
            if (!toss.heads()) {
                p.sendSystemMessage(Component.literal("§7계약 실패! 다시 시도할 수 있습니다."));
                continue;
            }
            List<ServerPlayer> destinations = server.getPlayerList().getPlayers().stream()
                    .filter(m -> m != p && HuntManager.isMonsterAlive(m) && !SackManager.isSacked(m)).toList();
            if (destinations.isEmpty()) {
                p.sendSystemMessage(Component.literal("§7순간이동할 다른 생존 몬스터가 없습니다. 쿨타임은 소모되지 않습니다."));
                continue;
            }
            ServerPlayer target = destinations.get(p.getRandom().nextInt(destinations.size()));
            {
                p.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), p.getYRot(), p.getXRot());
                p.resetFallDistance();
                COOLDOWNS.put(p.getUUID(), now + 120 * 20);
                p.sendSystemMessage(Component.literal("§6계약 성공! " + target.getName().getString() + "에게 순간이동했습니다."));
            }
        }
    }
    public static void reset() { ACTIVE.clear(); COOLDOWNS.clear(); }
    public static void logout(Player p) { ACTIVE.remove(p.getUUID()); }
}
