package com.halloweenhunt.client;

import com.halloweenhunt.HalloweenHunt;
import com.halloweenhunt.MedusaAppearance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Attaches the snakes to the head pivot, so looking, crouching and scaling follow the player. */
public class MedusaSnakeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HalloweenHunt.MODID, "textures/item/snake.png");
    public MedusaSnakeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        MedusaSnakeModel model = MedusaSnakeModel.loaded();
        if (model == null || !MedusaAppearance.visible(player.getUUID()) || player.isSpectator() || player.isInvisible()) return;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        pose.translate(0, -0.49, 0);
        pose.scale(0.55F, -0.55F, -0.55F);
        pose.translate(0, 0, -0.375);
        model.render(pose.last(), buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                (player.tickCount + partialTick) / 20.0, MedusaAppearance.castSeconds(player.getUUID()));
        pose.popPose();
    }
}
