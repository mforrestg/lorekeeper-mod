package com.example;

import com.example.entity.LorekeeperEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LorekeeperEntities {
    public static final EntityType<LorekeeperEntity> LOREKEEPER;

    static {
        Identifier id = Identifier.of(LorekeeperMod.MOD_ID, "lorekeeper");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        LOREKEEPER = Registry.register(
            Registries.ENTITY_TYPE,
            id,
            EntityType.Builder.create(LorekeeperEntity::new, SpawnGroup.CREATURE)
                .dimensions(0.6f, 1.95f)
                .maxTrackingRange(10)
                .build(key)
        );
    }

    private LorekeeperEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(LOREKEEPER, VillagerEntity.createVillagerAttributes());
    }
}
