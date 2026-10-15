package com.halloweenhunt;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class CoinItem extends Item {
    public CoinItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) { return Component.literal("계약의 동전"); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player instanceof ServerPlayer p) CoinTosses.start(p);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer p) CoinTosses.start(p);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
