package com.halloweenhunt;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class HuntItems {
    public static final String KEY = "hunt_item";
    public static final String STICK = "bugistick";
    public static final String ROPE = "rope";
    public static final String LANTERN = "lantern";
    public static final String DASH = "dash";

    private HuntItems() {}

    public static ItemStack tag(ItemStack s, String id) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putString(KEY, id));
        return s;
    }

    public static boolean is(ItemStack s, String id) {
        if (s.isEmpty()) return false;
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && id.equals(d.copyTag().getString(KEY));
    }

    private static ItemStack named(ItemStack s, String id, String name, ChatFormatting color) {
        s.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(st -> st.withItalic(false).withColor(color)));
        return tag(s, id);
    }

    private static ItemStack unbreakable(ItemStack s) {
        s.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return s;
    }

    // ───────────── 술래 ─────────────
    public static void giveHunterKit(ServerPlayer p) {
        p.getInventory().setItem(0, unbreakable(named(new ItemStack(Items.NETHERITE_SWORD), "sword", "네더의 검", ChatFormatting.DARK_RED)));
        p.getInventory().setItem(1, unbreakable(named(new ItemStack(Items.CROSSBOW), "crossbow", "석궁", ChatFormatting.GOLD)));
        p.getInventory().setItem(2, new ItemStack(Items.SPECTRAL_ARROW, 3)); // 발광 화살 3개
        p.getInventory().setItem(3, named(new ItemStack(Items.LANTERN), LANTERN, "사냥꾼의 랜턴 (4분부터)", ChatFormatting.YELLOW));
        p.getInventory().setItem(4, named(new ItemStack(Items.FEATHER), DASH, "대쉬", ChatFormatting.AQUA));
    }

    // ───────────── 망태할아버지 ─────────────
    public static void giveMangtaeKit(ServerPlayer p) {
        // 막대기: stick + custom_model_data 2, 밀치기 3, 내구도 10(=10회)
        ItemStack stick = new ItemStack(Items.STICK);
        stick.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(2));
        stick.set(DataComponents.MAX_STACK_SIZE, 1);
        stick.set(DataComponents.MAX_DAMAGE, 10);
        stick.set(DataComponents.DAMAGE, 0);
        HolderLookup.RegistryLookup<Enchantment> ench = p.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        stick.enchant(ench.getOrThrow(Enchantments.KNOCKBACK), 3);
        named(stick, STICK, "망태할아버지의 막대기", ChatFormatting.GOLD);

        // 망태기에 넣고 빼는 아이템: 밧줄(lead)
        ItemStack rope = named(new ItemStack(Items.LEAD), ROPE, "망태 밧줄", ChatFormatting.GREEN);

        p.getInventory().setItem(0, stick);
        p.getInventory().setItem(1, rope);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.MANGTAE_SACK.get()));
    }
}
