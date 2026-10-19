package com.halloweenhunt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Selected vanilla 1.21.1 cues. Volume and pitch match the user's playsound auditions. */
public final class RoleSounds {
    public record Cue(String id, float volume, float pitch) {}
    public static final Cue CAPTURE = new Cue("block.trial_spawner.spawn_item_begin", 2, 1);
    public static final Cue SACK_OPEN = new Cue("block.ender_chest.open", 0.8F, 0.6F);
    public static final Cue FRANKEN_REGEN = new Cue("block.trial_spawner.ambient_ominous", 1, 0);
    public static final Cue REVIVE = new Cue("entity.zombie_villager.cure", 0.8F, 0.8F);
    public static final Cue PETRIFY_CAST = new Cue("block.trial_spawner.spawn_mob", 1, 1);
    public static final Cue PETRIFY_SPELL = new Cue("entity.evoker.cast_spell", 0.8F, 0.7F);
    public static final Cue PETRIFY_HIT = new Cue("block.stone.break", 1, 0.6F);
    public static final Cue PETRIFY_RELEASE = new Cue("block.glass.break", 0.8F, 0.7F);
    public static final Cue COIN_FLICK = new Cue("block.trial_spawner.eject_item", 1, 2);
    public static final Cue CONTRACT = new Cue("entity.player.teleport", 1, 1);
    public static final Cue JACK_TRANSFORM = new Cue("entity.lightning_bolt.impact", 2, 1);
    public static final Cue JACK_FIRE = new Cue("block.fire.ambient", 5, 2);
    public static final Cue FIREBALL = new Cue("item.firecharge.use", 1, 1);
    private record Pending(UUID player, int due, Cue cue) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    private RoleSounds() {}

    public static void play(ServerPlayer player, Cue cue) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("minecraft", cue.id()));
        if (sound != null) player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.MASTER, cue.volume(), cue.pitch());
    }
    public static void later(ServerPlayer player, int ticks, Cue cue) {
        PENDING.add(new Pending(player.getUUID(), player.server.getTickCount() + ticks, cue));
    }
    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        PENDING.removeIf(pending -> {
            if (now < pending.due()) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (player != null && player.isAlive() && !player.isSpectator()) play(player, pending.cue());
            return true;
        });
    }
    public static void clearPlayer(UUID player) { PENDING.removeIf(p -> p.player().equals(player)); }
    public static void reset() { PENDING.clear(); }
}
