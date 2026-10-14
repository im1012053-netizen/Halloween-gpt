package com.halloweenhunt.client;

import com.halloweenhunt.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 상체 슬롯에 망태기를 끼면 등에 망태기 모델을 그린다. */
public class SackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(ModItems.MANGTAE_SACK.get()) || player.isInvisible()) return;

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // 몸통 기준: +y = 아래, +z = 등 쪽. 숫자를 바꿔 위치/크기 조절
        pose.translate(0.0F, 0.42F, 0.40F);
        pose.scale(0.55F, -0.55F, -0.55F);
        pose.translate(0.0F, -0.125F, 0.0F); // 모델 중심을 원점으로 (모델이 높이 20이라 약간 보정)
        Minecraft.getInstance().getItemRenderer().renderStatic(chest, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffer, player.level(), player.getId());
        pose.popPose();
    }
}
