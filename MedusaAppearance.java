package com.halloweenhunt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Server-owned Medusa appearance. No client-only classes are loaded on a dedicated server. */
public final class MedusaAppearance {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named("halloweenhunt:medusa")
            .networkProtocolVersion(1).simpleChannel();
    private static final Set<UUID> SERVER_MEDUSAS = new HashSet<>();
    private static final Set<UUID> CLIENT_MEDUSAS = new HashSet<>();
    private static final Map<UUID, Long> CLIENT_CASTS = new HashMap<>();
    private record State(UUID player, boolean visible, boolean cast) {}

    private MedusaAppearance() {}

    public static void init() {
        CHANNEL.messageBuilder(State.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((s, b) -> { b.writeUUID(s.player()); b.writeBoolean(s.visible()); b.writeBoolean(s.cast()); })
                .decoder(b -> new State(b.readUUID(), b.readBoolean(), b.readBoolean()))
                .consumerMainThread((s, context) -> {
                    if (s.visible()) CLIENT_MEDUSAS.add(s.player());
                    else { CLIENT_MEDUSAS.remove(s.player()); CLIENT_CASTS.remove(s.player()); }
                    if (s.cast() && s.visible()) CLIENT_CASTS.put(s.player(), System.nanoTime());
                }).add();
        CHANNEL.build();
    }

    public static boolean visible(UUID id) { return CLIENT_MEDUSAS.contains(id); }
    public static double castSeconds(UUID id) {
        Long start = CLIENT_CASTS.get(id);
        if (start == null) return -1;
        double time = (System.nanoTime() - start) / 1_000_000_000.0;
        if (time > 1.1) { CLIENT_CASTS.remove(id); return -1; }
        return time;
    }
    public static void clearClient() { CLIENT_MEDUSAS.clear(); CLIENT_CASTS.clear(); }
    public static void clearServer() { SERVER_MEDUSAS.clear(); }

    private static boolean shouldShow(ServerPlayer player) {
        return MonsterRoles.role(player) == MonsterRole.MEDUSA && !HuntManager.isHunter(player)
                && !player.isSpectator() && player.isAlive()
                && (!HuntManager.isRunning() || HuntManager.isMonsterAlive(player));
    }

    /** Runs even in the lobby/head start; only changed states are broadcast. */
    public static void tick(MinecraftServer server) {
        Set<UUID> current = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (shouldShow(player)) current.add(player.getUUID());
        }
        Set<UUID> changes = new HashSet<>(SERVER_MEDUSAS);
        changes.addAll(current);
        for (UUID id : changes) {
            if (SERVER_MEDUSAS.contains(id) != current.contains(id))
                CHANNEL.send(new State(id, current.contains(id), false), PacketDistributor.ALL.noArg());
        }
        SERVER_MEDUSAS.clear(); SERVER_MEDUSAS.addAll(current);
    }

    /** New viewers get everyone, including role-command overrides; no name guessing on clients. */
    public static void sync(ServerPlayer viewer) {
        for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
            CHANNEL.send(new State(player.getUUID(), shouldShow(player), false), PacketDistributor.PLAYER.with(viewer));
        }
    }

    /** Called only after petrification passes the server cooldown/ability checks. */
    public static void cast(ServerPlayer player) {
        if (shouldShow(player))
            CHANNEL.send(new State(player.getUUID(), true, true), PacketDistributor.ALL.noArg());
    }
}
