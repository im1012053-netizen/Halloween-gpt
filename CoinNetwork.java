package com.halloweenhunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

public final class CoinNetwork {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named("halloweenhunt:coin")
            .networkProtocolVersion(1).simpleChannel();
    public record Animation(boolean heads, long startedNanos) {
        public double seconds() { return (System.nanoTime() - startedNanos) / 1_000_000_000.0; }
    }
    private record Result(UUID player, boolean heads) {}
    private static final Map<UUID, Animation> ANIMATIONS = new HashMap<>();
    private CoinNetwork() {}
    public static void init() {
        CHANNEL.messageBuilder(Result.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((r, b) -> { b.writeUUID(r.player()); b.writeBoolean(r.heads()); })
                .decoder(b -> new Result(b.readUUID(), b.readBoolean()))
                .consumerMainThread((r, ctx) -> ANIMATIONS.put(r.player(), new Animation(r.heads(), System.nanoTime())))
                .add();
        CHANNEL.build();
    }
    public static Animation animation(UUID player) { return ANIMATIONS.get(player); }
    public static void clearClient() { ANIMATIONS.clear(); }
    public static void send(ServerPlayer player, boolean heads) {
        CHANNEL.send(new Result(player.getUUID(), heads), PacketDistributor.PLAYER.with(player));
    }
}
