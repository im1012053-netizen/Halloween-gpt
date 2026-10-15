package com.halloweenhunt.client;

import com.halloweenhunt.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HalloweenHunt.MODID, value = Dist.CLIENT)
public final class CoinRenderEvents {
    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(ModItems.COIN.get())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()) return;
        event.setCanceled(true);
        CoinNetwork.Animation animation = CoinNetwork.animation(mc.player.getUUID());
        boolean heads = animation != null && animation.heads();
        double seconds = animation == null ? 3 : Math.min(3, animation.seconds());
        CoinMotion.Frame frame = CoinMotion.frame(seconds, heads);
        var pose = event.getPoseStack();
        var buffers = event.getMultiBufferSource();
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);

        // Vanilla right-arm pose, lowered and with a small flick/catch movement.
        pose.pushPose();
        pose.translate(0.375, -0.8185 + frame.handLift(), -0.72);
        pose.mulPose(Axis.YP.rotationDegrees(45));
        pose.translate(-1, 3.6, 3.5);
        pose.mulPose(Axis.ZP.rotationDegrees(120));
        pose.mulPose(Axis.XP.rotationDegrees(200));
        pose.mulPose(Axis.YP.rotationDegrees(-135));
        pose.translate(5.6, 0, 0);
        // Rotate around the palm instead of around the camera to keep the catch aligned.
        pose.translate(-0.375, 0.75, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-frame.handLift() * 100));
        pose.translate(0.375, -0.75, 0);
        renderer.renderRightHand(pose, buffers, event.getPackedLight(), mc.player);
        pose.popPose();

        pose.pushPose();
        pose.translate(frame.x(), frame.y(), frame.z());
        pose.mulPose(Axis.XP.rotationDegrees(frame.spin()));
        pose.scale(0.34F, 0.34F, 0.34F);
        // Center the supplied Blockbench model (8,6,8.5) before spinning it.
        pose.translate(0, 0.125, -0.03125);
        mc.getItemRenderer().renderStatic(event.getItemStack(), ItemDisplayContext.NONE, event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { CoinNetwork.clearClient(); }
}
