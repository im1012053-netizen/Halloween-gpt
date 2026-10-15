package com.halloweenhunt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Vanilla networked fireball appearance, with no terrain ignition or friendly fire. */
public final class HuntFireball extends SmallFireball {
    public HuntFireball(Level level, LivingEntity owner, Vec3 direction) { super(level, owner, direction); }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!level().isClientSide && getOwner() instanceof ServerPlayer owner
                && HuntManager.isRunning() && hit.getEntity() instanceof ServerPlayer target && HuntManager.isHunter(target)) {
            target.hurt(damageSources().fireball(this, owner), 4.0F);
        }
        if (!level().isClientSide) discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) { if (!level().isClientSide) discard(); }
    @Override public void tick() { super.tick(); if (!level().isClientSide && (tickCount > 80 || !HuntManager.isRunning())) discard(); }
}
