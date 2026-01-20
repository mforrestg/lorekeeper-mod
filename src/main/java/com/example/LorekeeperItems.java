package com.example;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LorekeeperItems {
    public static final Item LOREKEEPER_SPAWN_EGG;

    static {
        Identifier id = Identifier.of(LorekeeperMod.MOD_ID, "lorekeeper_spawn_egg");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        LOREKEEPER_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            id,
            new SpawnEggItem(new Item.Settings().registryKey(key).spawnEgg(LorekeeperEntities.LOREKEEPER))
        );
    }

    private LorekeeperItems() {}

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
            .register(entries -> entries.add(LOREKEEPER_SPAWN_EGG));
    }
}
