package com.halloweenhunt.client;

import com.halloweenhunt.HalloweenHunt;
import com.halloweenhunt.MedusaAppearance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HalloweenHunt.MODID, value = Dist.CLIENT)
public final class MedusaClientEvents {
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { MedusaAppearance.clearClient(); }
}
