package com.halloweenhunt;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HalloweenHunt.MODID);

    /** 망태기: 상체(CHEST) 슬롯에 착용 가능한 아이템 */
    public static final RegistryObject<Item> MANGTAE_SACK =
            ITEMS.register("mangtae_sack", () -> new SackItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
