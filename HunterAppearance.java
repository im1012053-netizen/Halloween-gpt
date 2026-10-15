package com.halloweenhunt;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Server-authoritative round appearance; this class also loads on dedicated servers. */
public final class HunterAppearance {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named("halloweenhunt:appearance")
            .networkProtocolVersion(1).simpleChannel();
    private static UUID clientHunter;

    private record State(UUID hunter) {}

    private HunterAppearance() {}

    public static void init() {
        CHANNEL.messageBuilder(State.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((state, buf) -> {
                    buf.writeBoolean(state.hunter() != null);
                    if (state.hunter() != null) buf.writeUUID(state.hunter());
                })
                .decoder(buf -> new State(buf.readBoolean() ? buf.readUUID() : null))
                .consumerMainThread((state, context) -> clientHunter = state.hunter())
                .add();
        CHANNEL.build();
    }

    public static boolean isClientHunter(UUID id) {
        return id.equals(clientHunter);
    }

    public static void clearClient() { clientHunter = null; }

    public static void broadcast(UUID hunter) {
        CHANNEL.send(new State(hunter), PacketDistributor.ALL.noArg());
    }

    public static void sync(ServerPlayer player, UUID hunter) {
        CHANNEL.send(new State(hunter), PacketDistributor.PLAYER.with(player));
    }
}
