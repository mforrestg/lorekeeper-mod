package com.example;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public final class LorekeeperEncounterEvents {
    private LorekeeperEncounterEvents() {}

    public static void register() {
        UseBlockCallback.EVENT.register(LorekeeperEncounterEvents::onUseBlock);
        AttackEntityCallback.EVENT.register(LorekeeperEncounterEvents::onAttackEntity);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(LorekeeperEncounterEvents::onAfterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(LorekeeperEncounterEvents::onAfterDeath);
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(LorekeeperEncounterEvents::onAfterKilledOtherEntity);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(LorekeeperEncounterEvents::onPlayerChangeWorld);
        ServerEntityEvents.EQUIPMENT_CHANGE.register(LorekeeperEncounterEvents::onEquipmentChange);
        ServerTickEvents.END_WORLD_TICK.register(LorekeeperEncounterEvents::onWorldTick);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        BlockState state = world.getBlockState(hitResult.getBlockPos());
        LorekeeperEncounters.handleBlockUsed(serverWorld, serverPlayer, hitResult.getBlockPos(), state.getBlock());
        return ActionResult.PASS;
    }

    private static ActionResult onAttackEntity(
        PlayerEntity player,
        World world,
        Hand hand,
        net.minecraft.entity.Entity entity,
        EntityHitResult hitResult
    ) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            LorekeeperEncounters.recordCombat(serverPlayer);
        }
        return ActionResult.PASS;
    }

    private static void onAfterDamage(
        LivingEntity entity,
        DamageSource source,
        float baseDamageTaken,
        float damageTaken,
        boolean blocked
    ) {
        if (entity instanceof ServerPlayerEntity player) {
            LorekeeperEncounters.recordCombat(player);
        }
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player) {
            String deathMessage = source.getDeathMessage(player).getString();
            LorekeeperMilestones.record(player, deathMessage, "death", java.util.List.of("death"));
        }
    }

    private static void onAfterKilledOtherEntity(
        ServerWorld world,
        Entity entity,
        LivingEntity killedEntity,
        DamageSource source
    ) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        if (killedEntity == null || !isBoss(killedEntity.getType())) {
            return;
        }
        String name = killedEntity.getDisplayName().getString();
        LorekeeperMilestones.record(
            player,
            "Defeated " + name + ".",
            "boss_kill",
            java.util.List.of("boss", killedEntity.getType().toString())
        );
    }

    private static void onPlayerChangeWorld(ServerPlayerEntity player, ServerWorld origin, ServerWorld destination) {
        LorekeeperEncounters.handleWorldChange(destination, player, origin, destination);
    }

    private static void onEquipmentChange(
        LivingEntity livingEntity,
        EquipmentSlot slot,
        ItemStack previousStack,
        ItemStack currentStack
    ) {
        if (livingEntity instanceof ServerPlayerEntity player) {
            LorekeeperEncounters.handleEquipmentChange((ServerWorld) player.getEntityWorld(), player, slot, previousStack, currentStack);
        }
    }

    private static void onWorldTick(ServerWorld world) {
        LorekeeperEncounters.handleWorldTick(world);
    }

    private static boolean isBoss(EntityType<?> type) {
        return type == EntityType.ENDER_DRAGON
            || type == EntityType.WITHER
            || type == EntityType.ELDER_GUARDIAN
            || type == EntityType.WARDEN;
    }
}
