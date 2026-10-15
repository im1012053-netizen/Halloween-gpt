package com.halloweenhunt.client;


import com.halloweenhunt.HalloweenHunt;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = HalloweenHunt.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        HunterSkinEvents.reload(e.getContext());
        for (PlayerSkin.Model model : e.getSkins()) {
            EntityRenderer<?> r = e.getPlayerSkin(model);
            if (r instanceof PlayerRenderer pr) {
                pr.addLayer(new SackLayer(pr));
            }
        }
    }
}

