package com.halloweenhunt.client;

import com.halloweenhunt.HalloweenHunt;
import com.halloweenhunt.HunterAppearance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Changes the round hunter's body texture without modifying account skins. */
@Mod.EventBusSubscriber(modid = HalloweenHunt.MODID, value = Dist.CLIENT)
public final class HunterSkinEvents {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            HalloweenHunt.MODID, "textures/item/hunter_judge.png");
    private static PlayerRenderer renderer;
    private static boolean rendering;

    public static void reload(EntityRendererProvider.Context context) {
            renderer = new PlayerRenderer(context, false) {
                @Override
                public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
                    return TEXTURE;
                }
            };
            renderer.addLayer(new SackLayer(renderer));
    }

    @SubscribeEvent
    public static void render(RenderPlayerEvent.Pre event) {
        if (rendering || renderer == null || !HunterAppearance.isClientHunter(event.getEntity().getUUID())) return;
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        event.setCanceled(true);
        rendering = true;
        try {
            renderer.render(player, player.getYRot(), event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            rendering = false;
        }
    }

    @SubscribeEvent
    public static void arm(RenderArmEvent event) {
        if (renderer == null || !HunterAppearance.isClientHunter(event.getPlayer().getUUID())) return;
        event.setCanceled(true);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(event.getPlayer(), 0, 0, 0, 0, 0);
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        arm.xRot = 0;
        sleeve.xRot = 0;
        arm.visible = true;
        arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entitySolid(TEXTURE)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        if (event.getPlayer().isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
            sleeve.visible = true;
            sleeve.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEXTURE)),
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HunterAppearance.clearClient();
    }
}
