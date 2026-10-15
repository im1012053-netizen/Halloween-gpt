package com.halloweenhunt;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(HalloweenHunt.MODID)
public class HalloweenHunt {
    public static final String MODID = "halloweenhunt";

    @SuppressWarnings("removal")
    public HalloweenHunt() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        HunterAppearance.init();
        CoinNetwork.init();
    }
}

