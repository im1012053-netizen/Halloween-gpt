package com.halloweenhunt;

import java.util.*;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** All role state is server-only and scoped to one round. */
public final class MonsterRoles {
    public static final String PETRIFY = "medusa_petrify", HEAD = "medusa_head", JACK_LANTERN = "jack_lantern";
    public static final int PETRIFY_COOLDOWN = 60 * 20, JACK_COOLDOWN = 120 * 20;
    private static final ResourceLocation SCALE = ResourceLocation.fromNamespaceAndPath(HalloweenHunt.MODID, "franken_scale");
    private static final Map<UUID,Integer> PETRIFY_READY = new HashMap<>(), JACK_READY = new HashMap<>();
    private static final Map<UUID,Integer> IMMUNE = new HashMap<>(), REGEN = new HashMap<>(), FIRE_READY = new HashMap<>();
    private static final Map<UUID,Transform> TRANSFORMED = new HashMap<>();
    private static final Map<UUID,Frozen> FROZEN = new HashMap<>();
    private static final Set<UUID> REVIVE_USED = new HashSet<>();
    private static final List<ItemEntity> DROPPED_HEADS = new ArrayList<>();
    private record StoneWave(ServerLevel level, Vec3 origin, int startedAt) {}
    private static final List<StoneWave> STONE_WAVES = new ArrayList<>();
    private static final Map<UUID, Corpse> CORPSES = new HashMap<>();
    private static final RoleAssignments ASSIGNMENTS = new RoleAssignments();
    private record Frozen(ServerLevel level, Vec3 position, int until, int previousFrozenTicks) {}
    private record Transform(ItemStack previousHat, int until) {}
    private static final class Corpse {
        final UUID owner;
        final ArmorStand body;
        final ServerLevel level;
        final Vec3 position;
        final int expires;
        UUID rescuer;
        int progress;
        Corpse(ServerPlayer p, ArmorStand body) {
            owner = p.getUUID(); this.body = body; level = p.serverLevel(); position = p.position();
            expires = p.server.getTickCount() + 60 * 20;
        }
    }
    private MonsterRoles() {}
    public static MonsterRole role(Player p) { return ASSIGNMENTS.resolve(p.getUUID(), p.getGameProfile().getName()); }
    public static void clearAssignments() { ASSIGNMENTS.clear(); }
    public static boolean isFranken(Player p) { return role(p) == MonsterRole.FRANKENSTEIN && HuntManager.isMonsterAlive(p); }
    public static boolean isMedusa(Player p) { return role(p) == MonsterRole.MEDUSA && HuntManager.isMonsterAlive(p); }
    public static boolean isJack(Player p) { return role(p) == MonsterRole.JACK && HuntManager.isMonsterAlive(p); }
    public static boolean isTransformed(Player p) { return TRANSFORMED.containsKey(p.getUUID()); }
    public static boolean hasCorpse() { return !CORPSES.isEmpty(); }
    public static boolean isFrozen(Player p) { return FROZEN.containsKey(p.getUUID()); }
    public static boolean canAct(Player p) { return !p.isSpectator() && !isFrozen(p) && !(p instanceof ServerPlayer sp && SackManager.isSacked(sp)); }
    public static boolean immune(ServerPlayer p) { return p.server.getTickCount() < IMMUNE.getOrDefault(p.getUUID(), 0); }

    public static ItemStack model(ItemStack item, String id, String name, int model) {
        HuntItems.tag(item, id);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        return item;
    }
    public static void kit(ServerPlayer p) {
        var playerScale = p.getAttribute(Attributes.SCALE);
        if (playerScale != null) playerScale.setBaseValue(!HuntManager.isHunter(p) && role(p) == MonsterRole.FRANKENSTEIN ? 1.3 : 1.0);
        if (HuntManager.isHunter(p)) return;
        if (role(p) == MonsterRole.MANGTAE) {
            HuntItems.giveMangtaeKit(p);
        } else if (role(p) == MonsterRole.FRANKENSTEIN) {
            var scale = p.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.removeModifier(SCALE);
                scale.setBaseValue(HuntManager.isHunter(p) ? 1.0 : 1.3);
            }
            if (HuntManager.isHunter(p)) return;
            p.setItemSlot(EquipmentSlot.HEAD, model(new ItemStack(Items.CARVED_PUMPKIN), "franken_head", "프랑켄슈타인의 머리", 1002));
            p.getInventory().setItem(0, new ItemStack(Items.GOLDEN_CARROT, 3));
        } else if (role(p) == MonsterRole.MEDUSA && !HuntManager.isHunter(p)) {
            p.getInventory().setItem(0, model(new ItemStack(Items.STICK), PETRIFY, "석화 — 우클릭", 3));
        } else if (role(p) == MonsterRole.JACK && !HuntManager.isHunter(p)) {
            p.getInventory().setItem(0, new ItemStack(ModItems.COIN.get()));
            p.getInventory().setItem(1, model(new ItemStack(Items.LANTERN), JACK_LANTERN, "잭의 변신 — 변신 중 우클릭: 화염볼", 0));
        }
    }

    public static String assign(ServerPlayer p, MonsterRole requested) {
        if (HuntManager.isRunning() && (!HuntManager.isMonsterAlive(p) || HuntManager.isHunter(p)))
            return "게임 중에는 생존한 몬스터만 역할을 바꿀 수 있습니다. 술래 또는 탈락자는 게임 종료 후 지정하세요.";
        // Restore a transformation before deleting the old role's equipment.
        logout(p);
        SackManager.releaseCarrier(p);
        SackManager.release(p.server, p.getUUID());
        if (REGEN.remove(p.getUUID()) != null) p.removeEffect(MobEffects.REGENERATION);
        PETRIFY_READY.remove(p.getUUID()); JACK_READY.remove(p.getUUID());
        IMMUNE.remove(p.getUUID()); FIRE_READY.remove(p.getUUID()); REVIVE_USED.remove(p.getUUID());
        CoinTosses.resetPlayer(p);
        SackManager.resetCooldown(p);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            boolean oldRoleItem = item.is(ModItems.COIN.get()) || item.is(ModItems.MANGTAE_SACK.get());
            for (String id : new String[]{HuntItems.STICK, HuntItems.ROPE, PETRIFY, HEAD, JACK_LANTERN,
                    "franken_head", "mangtae_hat", "jack_head"}) oldRoleItem |= HuntItems.is(item, id);
            if (oldRoleItem) p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        ASSIGNMENTS.assign(p.getUUID(), requested);
        kit(p);
        p.sendSystemMessage(Component.literal("§6역할 변경: " + roleName(p) +
                (HuntManager.isRunning() ? " §f— 스킬을 사용할 수 있습니다." : " §f— /hunt start 후 스킬을 체험할 수 있습니다.")));
        return null;
    }

    public static String roleName(Player p) {
        if (role(p) == MonsterRole.MANGTAE) return "§6망태할아버지";
        if (role(p) == MonsterRole.FRANKENSTEIN) return "§2프랑켄슈타인";
        if (role(p) == MonsterRole.MEDUSA) return "§7메두사";
        if (role(p) == MonsterRole.JACK) return "§6잭";
        return "§a몬스터";
    }
    public static void start(MinecraftServer server) {
        reset(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (HuntManager.isParticipant(p)) kit(p);
    }

    public static void damaged(ServerPlayer p) {
        if (!isFranken(p) || p.getFoodData().getFoodLevel() < 2) return;
        p.getFoodData().setFoodLevel(p.getFoodData().getFoodLevel() - 2);
        p.getFoodData().setSaturation(0);
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        REGEN.put(p.getUUID(), p.server.getTickCount() + 25);
    }

    /** Called before spectator conversion, because vanilla inventory drops are suppressed by the minigame. */
    public static void death(ServerPlayer p) {
        if (isMedusa(p)) {
            // Exactly this new item drops, independently of keepInventory; all other inventory stays intact.
            ItemEntity drop = new ItemEntity(p.serverLevel(), p.getX(), p.getY() + 0.4, p.getZ(),
                    model(new ItemStack(Items.CARVED_PUMPKIN), HEAD, "메두사의 머리 — 석화 1회", 1003));
            drop.setDefaultPickUpDelay();
            drop.addTag("halloweenhunt_medusa_drop");
            p.serverLevel().addFreshEntity(drop);
            DROPPED_HEADS.add(drop);
        }
        if (isFranken(p) && REVIVE_USED.add(p.getUUID())) {
            ArmorStand body = new ArmorStand(p.serverLevel(), p.getX(), p.getY(), p.getZ());
            body.addTag("halloweenhunt_corpse");
            body.setYRot(p.getYRot()); body.setNoGravity(true); body.setInvulnerable(true);
            body.setShowArms(true); body.setNoBasePlate(true);
            body.setCustomNameVisible(true);
            body.setCustomName(Component.literal("§2프랑켄슈타인의 시체 §f— 웅크려 7초 부활"));
            body.setItemSlot(EquipmentSlot.HEAD, model(new ItemStack(Items.CARVED_PUMPKIN), "corpse_head", "시체", 1002));
            body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            body.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            body.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            body.setBodyPose(new Rotations(80, 0, 0));
            body.setHeadPose(new Rotations(60, 0, 0));
            body.setLeftArmPose(new Rotations(-80, 0, -10));
            body.setRightArmPose(new Rotations(-80, 0, 10));
            p.serverLevel().addFreshEntity(body);
            CORPSES.put(p.getUUID(), new Corpse(p, body));
            HuntManager.broadcast(p.server, Component.literal("§2프랑켄슈타인이 쓰러졌다! §f시체 옆에서 7초 동안 웅크리면 부활시킬 수 있다. (시체 60초 유지)"));
        }
        Transform form = TRANSFORMED.remove(p.getUUID());
        if (form != null) p.setItemSlot(EquipmentSlot.HEAD, form.previousHat());
        clearFrozen(p); REGEN.remove(p.getUUID()); IMMUNE.remove(p.getUUID());
    }

    public static boolean ability(ServerPlayer p, ItemStack stack) {
        boolean stone = HuntItems.is(stack, PETRIFY), head = HuntItems.is(stack, HEAD);
        boolean jack = HuntItems.is(stack, JACK_LANTERN);
        if (!stone && !head && !jack) return false;
        boolean lobbyTest = !HuntManager.isRunning() && stone && role(p) == MonsterRole.MEDUSA;
        if (!HuntManager.isMonsterAlive(p) && !lobbyTest) {
            p.displayClientMessage(Component.literal("§7스킬은 게임 시작 후 생존한 몬스터만 사용할 수 있습니다."), true);
            return true;
        }
        if (HuntManager.isHeadStart()) {
            p.displayClientMessage(Component.literal("§7술래 대기 30초가 끝나면 스킬을 사용할 수 있습니다."), true);
            return true;
        }
        if (!canAct(p)) {
            p.displayClientMessage(Component.literal("§7석화 또는 망태기 안에서는 스킬을 사용할 수 없습니다."), true);
            return true;
        }
        if (head || (stone && (isMedusa(p) || lobbyTest))) petrify(p, stack, head);
        if (jack && isJack(p)) {
            if (isTransformed(p)) fire(p); else transform(p);
        }
        return true;
    }
    public static boolean isAbility(ItemStack stack) {
        return HuntItems.is(stack, PETRIFY) || HuntItems.is(stack, HEAD) || HuntItems.is(stack, JACK_LANTERN);
    }
    private static void petrify(ServerPlayer p, ItemStack stack, boolean disposable) {
        int now = p.server.getTickCount();
        int ready = PETRIFY_READY.getOrDefault(p.getUUID(), 0);
        if (!disposable && now < ready) {
            p.displayClientMessage(Component.literal("§7석화 쿨타임 " + ((ready - now + 19) / 20) + "초"), true); return;
        }
        MedusaAppearance.cast(p);
        ServerLevel level = p.serverLevel();
        for (ServerPlayer target : p.server.getPlayerList().getPlayers()) {
            if (target == p || target.serverLevel() != level
                    || (HuntManager.isRunning() && !HuntManager.isParticipant(target))
                    || target.isSpectator() || !target.isAlive() || SackManager.isSacked(target)
                    || target.position().distanceToSqr(p.position()) > 25) continue;
            Frozen previous = FROZEN.get(target.getUUID());
            int previousTicks = previous == null ? target.getTicksFrozen() : previous.previousFrozenTicks();
            FROZEN.put(target.getUUID(), new Frozen(level, target.position(), now + 40, previousTicks));
            // Keep above the full-freeze threshold despite vanilla thawing between server ticks.
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + 20);
            target.displayClientMessage(Component.literal("§7석화! 2초 동안 움직일 수 없습니다."), true);
        }
        // This is intentionally outside the target loop: a cast with zero victims still emits the wave.
        StoneWave wave = new StoneWave(level, p.position(), now);
        STONE_WAVES.add(wave);
        emitStoneWave(wave, now);
        level.playSound(null, p.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1, 0.6F);
        if (disposable) stack.shrink(1); else PETRIFY_READY.put(p.getUUID(), now + (HuntManager.isRunning() ? PETRIFY_COOLDOWN : 20));
    }
    private static void emitStoneWave(StoneWave wave, int now) {
        double progress = Math.min(1.0, Math.max(0.0, (now - wave.startedAt()) / 24.0));
        double radius = 0.35 + 4.65 * progress;
        DustParticleOptions gray = new DustParticleOptions(new Vector3f(0.72F, 0.72F, 0.72F), 2.0F);
        // Explicit forced packets keep the skill visible with reduced particle settings.
        // Two rising bands make expansion visible both in first person and to nearby players.
        for (ServerPlayer viewer : wave.level().players()) {
            if (viewer.position().distanceToSqr(wave.origin()) > 48 * 48) continue;
            for (int i = 0; i < 48; i++) {
                double angle = i * Math.PI / 24;
                double x = wave.origin().x + Math.cos(angle) * radius;
                double z = wave.origin().z + Math.sin(angle) * radius;
                for (double height : new double[]{0.3, 1.15}) {
                    wave.level().sendParticles(viewer, gray, true, x, wave.origin().y + height, z,
                            1, 0.025, 0.06, 0.025, 0);
                }
            }
        }
    }
    private static void transform(ServerPlayer p) {
        int now = p.server.getTickCount(), ready = JACK_READY.getOrDefault(p.getUUID(), 0);
        if (now < ready) {
            p.displayClientMessage(Component.literal("§6변신 쿨타임 " + ((ready - now + 19) / 20) + "초"), true); return;
        }
        TRANSFORMED.put(p.getUUID(), new Transform(p.getItemBySlot(EquipmentSlot.HEAD).copy(), now + 200));
        IMMUNE.put(p.getUUID(), now + 200);
        JACK_READY.put(p.getUUID(), now + JACK_COOLDOWN);
        p.setItemSlot(EquipmentSlot.HEAD, model(new ItemStack(Items.CARVED_PUMPKIN), "jack_head", "잭 오 랜턴", 1004));
        p.serverLevel().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.7, 0.4, 0.02);
        p.sendSystemMessage(Component.literal("§610초 동안 무적! 랜턴을 다시 우클릭하면 화염볼을 발사합니다."));
    }
    private static void fire(ServerPlayer p) {
        int now = p.server.getTickCount();
        if (now < FIRE_READY.getOrDefault(p.getUUID(), 0)) return;
        Vec3 look = p.getLookAngle();
        HuntFireball ball = new HuntFireball(p.serverLevel(), p, look);
        ball.setPos(p.getX() + look.x * 0.6, p.getEyeY() + look.y * 0.6, p.getZ() + look.z * 0.6);
        ball.setDeltaMovement(look.scale(1.2));
        p.serverLevel().addFreshEntity(ball);
        FIRE_READY.put(p.getUUID(), now + 10);
        p.level().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1, 1);
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        tickPetrification(server);
        TRANSFORMED.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null && HuntManager.isMonsterAlive(p) && now < entry.getValue().until()) return false;
            if (p != null) p.setItemSlot(EquipmentSlot.HEAD, entry.getValue().previousHat()); return true;
        });
        REGEN.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !isFranken(p) || !p.hasEffect(MobEffects.REGENERATION)) return true;
            if (now >= entry.getValue()) { HuntManager.healControlled(p, 1.0F); entry.setValue(now + 25); }
            return false;
        });
        IMMUNE.entrySet().removeIf(entry -> now >= entry.getValue());
        for (Corpse body : new ArrayList<>(CORPSES.values())) tickCorpse(server, now, body);
    }
    /** The lobby runs only petrification timers, without enabling other roles' round skills. */
    public static void tickLobby(MinecraftServer server) {
        if (!HuntManager.isRunning()) tickPetrification(server);
    }

    private static void tickPetrification(MinecraftServer server) {
        int now = server.getTickCount();
        STONE_WAVES.removeIf(wave -> {
            int age = now - wave.startedAt();
            if (age > 24) return true;
            if (age > 0 && age % 2 == 0) emitStoneWave(wave, now);
            return false;
        });
        FROZEN.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey()); Frozen f = entry.getValue();
            if (p == null) return true;
            if ((HuntManager.isRunning() && !HuntManager.isParticipant(p)) || p.isSpectator() || SackManager.isSacked(p) || now >= f.until()) {
                p.setTicksFrozen(f.previousFrozenTicks());
                return true;
            }
            p.setTicksFrozen(p.getTicksRequiredToFreeze() + 20);
            p.teleportTo(f.level(), f.position().x, f.position().y, f.position().z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(0, 0, 0); p.resetFallDistance(); return false;
        });
    }

    private static void tickCorpse(MinecraftServer server, int now, Corpse corpse) {
        ServerPlayer owner = server.getPlayerList().getPlayer(corpse.owner);
        if (owner == null || now >= corpse.expires || corpse.body.isRemoved()) { removeCorpse(corpse.owner); return; }
        ServerPlayer rescuer = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != owner && HuntManager.isMonsterAlive(p) && canAct(p) && p.isShiftKeyDown()
                    && p.serverLevel() == corpse.level && p.position().distanceToSqr(corpse.position) <= 9) {
                if (p.getUUID().equals(corpse.rescuer)) { rescuer = p; break; }
                if (rescuer == null) rescuer = p;
            }
        }
        if (rescuer == null) { corpse.rescuer = null; corpse.progress = 0; return; }
        if (!rescuer.getUUID().equals(corpse.rescuer)) { corpse.rescuer = rescuer.getUUID(); corpse.progress = 0; }
        corpse.progress++;
        if (corpse.progress % 10 == 0) rescuer.displayClientMessage(Component.literal("§2부활 중 " + (corpse.progress / 20.0) + " / 7초"), true);
        if (corpse.progress >= 140) {
            owner.teleportTo(corpse.level, corpse.position.x, corpse.position.y, corpse.position.z, owner.getYRot(), owner.getXRot());
            HuntManager.revive(owner);
            owner.removeEffect(MobEffects.REGENERATION);
            owner.setHealth(owner.getMaxHealth() / 2);
            owner.resetFallDistance();
            IMMUNE.put(owner.getUUID(), now + 60);
            removeCorpse(corpse.owner);
            HuntManager.broadcast(server, Component.literal("§2프랑켄슈타인 부활! §f3초 동안 무적입니다. 부활 기회는 소모되었습니다."));
        }
    }
    private static void removeCorpse(UUID owner) {
        Corpse removed = CORPSES.remove(owner);
        if (removed != null) removed.body.discard();
    }
    private static void clearFrozen(ServerPlayer p) {
        Frozen previous = FROZEN.remove(p.getUUID());
        if (previous != null) p.setTicksFrozen(previous.previousFrozenTicks());
    }

    public static void logout(ServerPlayer p) {
        removeCorpse(p.getUUID());
        clearFrozen(p);
        Transform t = TRANSFORMED.remove(p.getUUID());
        if (t != null) p.setItemSlot(EquipmentSlot.HEAD, t.previousHat());
    }
    public static void reset(MinecraftServer server) {
        for (var entry : FROZEN.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) p.setTicksFrozen(entry.getValue().previousFrozenTicks());
        }
        for (var entry : TRANSFORMED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) p.setItemSlot(EquipmentSlot.HEAD, entry.getValue().previousHat());
        }
        for (Corpse body : CORPSES.values()) body.body.discard();
        CORPSES.clear();
        for (ItemEntity drop : DROPPED_HEADS) if (!drop.isRemoved()) drop.discard();
        STONE_WAVES.clear();
        DROPPED_HEADS.clear(); PETRIFY_READY.clear(); JACK_READY.clear(); IMMUNE.clear();
        REGEN.clear(); FIRE_READY.clear(); TRANSFORMED.clear(); FROZEN.clear(); REVIVE_USED.clear();
    }
}


