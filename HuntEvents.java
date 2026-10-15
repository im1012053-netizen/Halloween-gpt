package com.halloweenhunt;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = HalloweenHunt.MODID)
public class HuntEvents {

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) HuntManager.tick(server);
    }

    // ───────────── /hunt start | stop | reset ─────────────
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("hunt")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(c -> {
                    String err = HuntManager.start(c.getSource().getServer());
                    if (err != null) c.getSource().sendFailure(Component.literal(err));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(c -> {
                    HuntManager.stop(c.getSource().getServer(), Component.literal("§7게임이 강제 종료되었습니다."));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    HuntManager.resetPastHunters();
                    c.getSource().sendSuccess(() -> Component.literal("술래 기록을 초기화했습니다."), true);
                    return Command.SINGLE_SUCCESS;
                })));
    }

    // ───────────── 데미지 규칙 ─────────────
    /** 공격 자체를 막을지 결정 */
    @SubscribeEvent
    public static void attack(LivingAttackEvent e) {
        if (e.getEntity().getTags().contains("halloweenhunt_corpse")) { e.setCanceled(true); return; }
        if (!HuntManager.isRunning()) return;
        if (!(e.getEntity() instanceof ServerPlayer target) || !HuntManager.isParticipant(target)) return;

        // Head start is for hiding: no combat until the hunter is released.
        if (HuntManager.isHeadStart()) {
            e.setCanceled(true);
            return;
        }

        // 망태기 속 플레이어는 무적
        if (SackManager.isSacked(target) || MonsterRoles.immune(target) || target.isSpectator()) {
            e.setCanceled(true);
            return;
        }
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer attacker && HuntManager.isParticipant(attacker)) {
            if (!MonsterRoles.canAct(attacker)) {
                e.setCanceled(true);
                return;
            }
            if (HuntManager.isHunter(attacker)) return; // 술래 → 몬스터: 일반 데미지 (검/석궁)

            // 몬스터가 때림
            if (HuntManager.isHunter(target)) {
                boolean stick = HuntItems.is(attacker.getMainHandItem(), HuntItems.STICK)
                        && e.getSource().getDirectEntity() == attacker;
                boolean bareHand = attacker.getMainHandItem().isEmpty()
                        && e.getSource().getDirectEntity() == attacker;
                boolean fireball = e.getSource().getDirectEntity() instanceof HuntFireball && MonsterRoles.isJack(attacker);
                if (!stick && !bareHand && !fireball) e.setCanceled(true);
            } else {
                e.setCanceled(true); // 몬스터끼리 공격 불가
            }
        }
    }

    /** 막대기 명중: 데미지는 거의 없고(밀치기 3만 적용) 사용 횟수 1 소모 */
    @SubscribeEvent
    public static void hurt(LivingHurtEvent e) {
        if (!HuntManager.isRunning()) return;
        if (!(e.getEntity() instanceof ServerPlayer target) || !HuntManager.isHunter(target)) return;
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer attacker && HuntManager.isParticipant(attacker) && !HuntManager.isHunter(attacker)) {
            ItemStack held = attacker.getMainHandItem();
            if (HuntItems.is(held, HuntItems.STICK) && e.getSource().getDirectEntity() == attacker) {
                e.setAmount(0.01f);
                HuntManager.onStickHit(attacker, held);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent e) {
        if (e.getAmount() > 0 && e.getEntity() instanceof ServerPlayer p) {
            HuntManager.recordDamage(p);
            MonsterRoles.damaged(p);
        }
    }

    /** Replace ordinary automatic healing during a round with the delayed recovery timer. */
    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && HuntManager.isParticipant(p)
                && !HuntManager.isApplyingRecovery()) {
            e.setCanceled(true);
        }
    }

    /** 죽으면 관전자로 전환(= 잡힘) */
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!HuntManager.isRunning()) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || !HuntManager.isParticipant(p)) return;
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        if (HuntManager.isMonsterAlive(p) && !SackManager.isSacked(p)) HuntManager.catchPlayer(p);
    }

    // ───────────── 우클릭 아이템 ─────────────
    /** 망태 밧줄을 다른 플레이어에게 우클릭 */
    @SubscribeEvent
    public static void interactEntity(PlayerInteractEvent.EntityInteract e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!HuntItems.is(e.getItemStack(), HuntItems.ROPE)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getEntity() instanceof ServerPlayer p && e.getTarget() instanceof ServerPlayer t) {
            SackManager.capture(p, t);
        }
    }

    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (ability(e.getEntity(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 랜턴 설치 / 밧줄 매듭 같은 바닐라 동작 방지 */
    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (ability(e.getEntity(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** 우리 아이템이면 true (호출한 쪽에서 이벤트 취소) */
    private static boolean ability(Player player, ItemStack stack) {
        if (stack.is(ModItems.COIN.get())) {
            if (player instanceof ServerPlayer sp) CoinTosses.start(sp);
            return true;
        }
        if (MonsterRoles.isAbility(stack)) {
            if (player instanceof ServerPlayer sp) MonsterRoles.ability(sp, stack);
            return true;
        }
        boolean rope = HuntItems.is(stack, HuntItems.ROPE);
        boolean lantern = HuntItems.is(stack, HuntItems.LANTERN);
        boolean dash = HuntItems.is(stack, HuntItems.DASH);
        if (!rope && !lantern && !dash) return false;
        if (player instanceof ServerPlayer sp) {
            if (rope && MonsterRoles.canAct(sp)) SackManager.releaseCarrier(sp);
            else if (lantern) HuntManager.useLantern(sp);
            else HuntManager.dash(sp);
        }
        return true;
    }

    @SubscribeEvent
    public static void headPickup(EntityItemPickupEvent e) {
        if (HuntItems.is(e.getItem().getItem(), MonsterRoles.HEAD)
                && (!HuntManager.isMonsterAlive(e.getEntity()) || !MonsterRoles.canAct(e.getEntity()))) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void corpseInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getTarget().getTags().contains("halloweenhunt_corpse")) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void corpseInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        if (e.getTarget().getTags().contains("halloweenhunt_corpse")) {
            e.setCanceled(true); e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void shutdown(ServerStoppingEvent e) {
        HuntManager.stop(e.getServer(), Component.literal("§7서버 종료로 게임을 종료했습니다."));
        MonsterRoles.reset(e.getServer()); CoinTosses.reset();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            if (HuntManager.isParticipant(p) && !HuntManager.isHunter(p) && !HuntManager.isMonsterAlive(p)) {
                p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            }
            HuntManager.giveLobbyKit(p);
            HunterAppearance.sync(p, HuntManager.currentHunter());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) HuntManager.onLogout(p);
    }
}



