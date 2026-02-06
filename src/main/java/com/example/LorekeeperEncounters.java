package com.example;

import com.example.entity.LorekeeperEntity;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public final class LorekeeperEncounters {
    private static final double DEFAULT_NEARBY_RADIUS = 16.0;
    private static final int DEFAULT_ENCOUNTER_COOLDOWN_SECONDS = 180;
    private static final int DEFAULT_COMBAT_COOLDOWN_SECONDS = 15;
    private static final int DEFAULT_CHUNK_STAY_MINUTES = 5;
    private static final int DEFAULT_DESPAWN_MINUTES = 10;
    private static final double DEFAULT_LUCK_RAMP_STEP = 0.05;
    private static final double DEFAULT_FIRST_ENCOUNTER_BONUS = 0.20;
    private static final int DEFAULT_MAX_SETTLEMENT_CHUNKS = 48;

    private static final Text INTRO_LINE_1 =
        Text.literal("Greetings. I am the Lore Keeper, a local historian.");
    private static final Text INTRO_LINE_GENERIC =
        Text.literal("I noticed you are working on something.");
    private static final Text INTRO_LINE_CRAFTING =
        Text.literal("I noticed you put down a crafting table and are working on something.");

    private static final String ACTION_ENCHANT_PLACED = "placed_enchanting_table";
    private static final String ACTION_ANVIL_PLACED = "placed_anvil";
    private static final String ACTION_BREWING_PLACED = "placed_brewing_stand";
    private static final String ACTION_ENCHANT_USED = "used_enchanting_table";
    private static final String ACTION_ANVIL_USED = "used_anvil";
    private static final String ACTION_BREWING_USED = "used_brewing_stand";
    private static final String ACTION_ENTERED_NETHER = "entered_nether";
    private static final String ACTION_ENTERED_END = "entered_end";
    private static final String ACTION_EQUIPPED_DIAMOND_PICKAXE = "equipped_diamond_pickaxe";
    private static final String ACTION_EQUIPPED_NETHERITE = "equipped_netherite";

    private LorekeeperEncounters() {}

    public static void handleBlockPlaced(ServerWorld world, ServerPlayerEntity player, BlockPos pos, Block block) {
        TriggerType trigger = triggerForPlacement(block);
        if (trigger == null) {
            return;
        }
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        boolean firstAction = false;

        if (trigger == TriggerType.ENCHANTING_TABLE_PLACED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_ENCHANT_PLACED);
        } else if (trigger == TriggerType.ANVIL_PLACED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_ANVIL_PLACED);
        } else if (trigger == TriggerType.BREWING_STAND_PLACED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_BREWING_PLACED);
        } else if (trigger == TriggerType.SETTLEMENT_PLACED) {
            long chunkKey = new ChunkPos(pos).toLong();
            if (!data.registerSettlementChunk(player.getUuid(), chunkKey, getMaxSettlementChunks())) {
                return;
            }
        }

        attemptEncounter(world, player, pos, trigger, firstAction);
    }

    public static void handleBlockUsed(ServerWorld world, ServerPlayerEntity player, BlockPos pos, Block block) {
        TriggerType trigger = triggerForUse(block);
        if (trigger == null) {
            return;
        }
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        boolean firstAction = false;
        if (trigger == TriggerType.ENCHANTING_TABLE_USED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_ENCHANT_USED);
        } else if (trigger == TriggerType.ANVIL_USED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_ANVIL_USED);
        } else if (trigger == TriggerType.BREWING_STAND_USED) {
            firstAction = data.markActionFlag(player.getUuid(), ACTION_BREWING_USED);
        }
        attemptEncounter(world, player, pos, trigger, firstAction);
    }

    public static void handleWorldChange(
        ServerWorld world,
        ServerPlayerEntity player,
        ServerWorld origin,
        ServerWorld destination
    ) {
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        RegistryKey<World> destinationKey = destination.getRegistryKey();
        if (destinationKey == World.NETHER) {
            boolean firstAction = data.markActionFlag(player.getUuid(), ACTION_ENTERED_NETHER);
            attemptEncounter(world, player, player.getBlockPos(), TriggerType.ENTER_NETHER, firstAction);
            String line = firstAction ? "Entered the Nether for the first time." : "Entered the Nether.";
            LorekeeperMilestones.record(
                player,
                line,
                "world_change",
                java.util.List.of("nether", firstAction ? "first_time" : "repeat")
            );
        } else if (destinationKey == World.END) {
            boolean firstAction = data.markActionFlag(player.getUuid(), ACTION_ENTERED_END);
            attemptEncounter(world, player, player.getBlockPos(), TriggerType.ENTER_END, firstAction);
            String line = firstAction ? "Entered the End for the first time." : "Entered the End.";
            LorekeeperMilestones.record(
                player,
                line,
                "world_change",
                java.util.List.of("end", firstAction ? "first_time" : "repeat")
            );
        } else if (destinationKey == World.OVERWORLD && origin != null && origin.getRegistryKey() != World.OVERWORLD) {
            LorekeeperMilestones.record(
                player,
                "Returned to the Overworld.",
                "world_change",
                java.util.List.of("overworld")
            );
        }
    }

    public static void handleEquipmentChange(
        ServerWorld world,
        ServerPlayerEntity player,
        EquipmentSlot slot,
        ItemStack previousStack,
        ItemStack currentStack
    ) {
        if (currentStack.isEmpty()) {
            return;
        }
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());

        if (slot == EquipmentSlot.MAINHAND && currentStack.isOf(Items.DIAMOND_PICKAXE)) {
            boolean firstAction = data.markActionFlag(player.getUuid(), ACTION_EQUIPPED_DIAMOND_PICKAXE);
            attemptEncounter(world, player, player.getBlockPos(), TriggerType.EQUIP_DIAMOND_PICKAXE, firstAction);
            return;
        }

        if (isNetheriteEquipment(currentStack)) {
            boolean firstAction = data.markActionFlag(player.getUuid(), ACTION_EQUIPPED_NETHERITE);
            attemptEncounter(world, player, player.getBlockPos(), TriggerType.EQUIP_NETHERITE, firstAction);
        }
    }

    public static void handleWorldTick(ServerWorld world) {
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        for (ServerPlayerEntity player : world.getPlayers()) {
            long chunkKey = player.getChunkPos().toLong();
            long stayTicks = data.updateChunkStay(player.getUuid(), chunkKey);
            if (stayTicks >= getChunkStayTicks()) {
                data.resetChunkStay(player.getUuid());
                attemptEncounter(world, player, player.getBlockPos(), TriggerType.CHUNK_STAY, false);
            }
        }
    }

    public static void recordCombat(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        data.markCombat(player.getUuid(), world.getTime());
    }

    private static TriggerType triggerForPlacement(Block block) {
        if (block == Blocks.CRAFTING_TABLE) {
            return TriggerType.CRAFTING_TABLE_PLACED;
        }
        if (block == Blocks.ENCHANTING_TABLE) {
            return TriggerType.ENCHANTING_TABLE_PLACED;
        }
        BlockState state = block.getDefaultState();
        if (state.isIn(BlockTags.ANVIL)) {
            return TriggerType.ANVIL_PLACED;
        }
        if (block == Blocks.BREWING_STAND) {
            return TriggerType.BREWING_STAND_PLACED;
        }
        if (isSettlementBlock(block, state)) {
            return TriggerType.SETTLEMENT_PLACED;
        }
        return null;
    }

    private static TriggerType triggerForUse(Block block) {
        if (block == Blocks.ENCHANTING_TABLE) {
            return TriggerType.ENCHANTING_TABLE_USED;
        }
        if (block == Blocks.BREWING_STAND) {
            return TriggerType.BREWING_STAND_USED;
        }
        BlockState state = block.getDefaultState();
        if (state.isIn(BlockTags.ANVIL)) {
            return TriggerType.ANVIL_USED;
        }
        return null;
    }

    private static boolean isSettlementBlock(Block block, BlockState state) {
        if (state.isIn(BlockTags.BEDS) || state.isIn(BlockTags.ALL_SIGNS)) {
            return true;
        }
        return block instanceof AbstractChestBlock;
    }

    private static void attemptEncounter(
        ServerWorld world,
        ServerPlayerEntity player,
        BlockPos anchorPos,
        TriggerType trigger,
        boolean firstAction
    ) {
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        long nowTicks = world.getTime();
        if (data.isOnEncounterCooldown(player.getUuid(), nowTicks, getEncounterCooldownTicks())) {
            return;
        }
        if (data.isInCombat(player.getUuid(), nowTicks, getCombatCooldownTicks())) {
            return;
        }
        if (isLorekeeperPresent(world)) {
            return;
        }
        if (isLorekeeperNearby(world, new Vec3d(player.getX(), player.getY(), player.getZ()))) {
            return;
        }

        LorekeeperConfig.EncounterTriggerConfig config = getTriggerConfig(trigger);
        double chance = config.baseChance;
        if (!data.hasEncountered(player.getUuid())) {
            chance += getFirstEncounterBonus();
        }
        if (firstAction) {
            chance += config.firstTimeBonus;
        }
        chance += data.getLuck(player.getUuid()) * getLuckRampStep();
        if (chance > config.maxChance) {
            chance = config.maxChance;
        }
        if (chance <= 0.0) {
            return;
        }

        if (world.getRandom().nextDouble() > chance) {
            data.incrementLuck(player.getUuid());
            return;
        }

        data.resetLuck(player.getUuid());
        LorekeeperEntity lorekeeper = spawnLorekeeper(world, player, anchorPos);
        if (lorekeeper == null) {
            return;
        }
        data.markEncounterTime(player.getUuid(), nowTicks);
        boolean firstEncounter = !data.hasEncountered(player.getUuid());
        if (firstEncounter) {
            data.markEncountered(player.getUuid());
        }
        handleEncounterDialog(player, trigger, firstEncounter);
    }

    private static void handleEncounterDialog(ServerPlayerEntity player, TriggerType trigger, boolean firstEncounter) {
        if (firstEncounter) {
            player.sendMessage(Text.literal("Lore Keeper: ").append(INTRO_LINE_1), false);
            player.sendMessage(Text.literal("Lore Keeper: ").append(introLineForTrigger(trigger)), false);
            LorekeeperInterviewManager.offerInterview(
                player,
                Text.literal("Would you be willing to conduct a short interview? "),
                LorekeeperInterviewManager.QuestionPool.BASIC
            );
            return;
        }

        String name = player.getName().getString();
        LorekeeperInterviewManager.offerInterview(
            player,
            Text.literal("Hello, " + name + ". Looks like you are working on something. Do you have time for a quick interview? "),
            LorekeeperInterviewManager.QuestionPool.MAIN
        );
    }

    private static Text introLineForTrigger(TriggerType trigger) {
        if (trigger == TriggerType.CRAFTING_TABLE_PLACED) {
            return INTRO_LINE_CRAFTING;
        }
        return INTRO_LINE_GENERIC;
    }

    private static LorekeeperEntity spawnLorekeeper(ServerWorld world, ServerPlayerEntity player, BlockPos anchorPos) {
        BlockPos spawnPos = findSpawnPos(world, player);
        LorekeeperEntity lorekeeper = LorekeeperEntities.LOREKEEPER.create(
            world,
            null,
            spawnPos,
            SpawnReason.EVENT,
            true,
            false
        );
        if (lorekeeper == null) {
            return null;
        }
        Vec3d center = Vec3d.ofCenter(spawnPos);
        lorekeeper.refreshPositionAndAngles(center.x, center.y, center.z, world.getRandom().nextFloat() * 360.0f, 0.0f);
        lorekeeper.setDespawnDelay(getDespawnDelayTicks());
        lorekeeper.setAnchorPos(anchorPos);
        world.spawnEntity(lorekeeper);
        return lorekeeper;
    }

    private static BlockPos findSpawnPos(ServerWorld world, ServerPlayerEntity player) {
        BlockPos base = player.getBlockPos();
        int radius = 4;
        int dx = world.getRandom().nextBetween(-radius, radius);
        int dz = world.getRandom().nextBetween(-radius, radius);
        BlockPos candidate = base.add(dx, 0, dz);
        BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate);
        return top.up();
    }

    private static boolean isLorekeeperNearby(ServerWorld world, Vec3d center) {
        double radius = getNearbyRadius();
        double radiusSq = radius * radius;
        return !world.getEntitiesByType(
            TypeFilter.instanceOf(LorekeeperEntity.class),
            entity -> entity.squaredDistanceTo(center) <= radiusSq
        ).isEmpty();
    }

    private static boolean isLorekeeperPresent(ServerWorld world) {
        return !world.getEntitiesByType(
            TypeFilter.instanceOf(LorekeeperEntity.class),
            entity -> true
        ).isEmpty();
    }

    private static boolean isNetheriteEquipment(ItemStack stack) {
        return stack.isOf(Items.NETHERITE_HELMET)
            || stack.isOf(Items.NETHERITE_CHESTPLATE)
            || stack.isOf(Items.NETHERITE_LEGGINGS)
            || stack.isOf(Items.NETHERITE_BOOTS)
            || stack.isOf(Items.NETHERITE_SWORD)
            || stack.isOf(Items.NETHERITE_PICKAXE)
            || stack.isOf(Items.NETHERITE_AXE)
            || stack.isOf(Items.NETHERITE_SHOVEL)
            || stack.isOf(Items.NETHERITE_HOE);
    }

    private enum TriggerType {
        CRAFTING_TABLE_PLACED("crafting_table_placed", 0.25, 0.85, 0.10),
        ENCHANTING_TABLE_PLACED("enchanting_table_placed", 0.25, 0.85, 0.15),
        ANVIL_PLACED("anvil_placed", 0.20, 0.80, 0.10),
        BREWING_STAND_PLACED("brewing_stand_placed", 0.20, 0.80, 0.10),
        SETTLEMENT_PLACED("settlement_placed", 0.15, 0.70, 0.05),
        ENCHANTING_TABLE_USED("enchanting_table_used", 0.20, 0.80, 0.15),
        ANVIL_USED("anvil_used", 0.20, 0.80, 0.10),
        BREWING_STAND_USED("brewing_stand_used", 0.20, 0.80, 0.10),
        ENTER_NETHER("enter_nether", 0.40, 0.90, 0.20),
        ENTER_END("enter_end", 0.50, 0.90, 0.25),
        EQUIP_DIAMOND_PICKAXE("equip_diamond_pickaxe", 0.30, 0.85, 0.20),
        EQUIP_NETHERITE("equip_netherite", 0.35, 0.90, 0.25),
        CHUNK_STAY("chunk_stay", 0.20, 0.75, 0.10);

        private final String id;
        private final LorekeeperConfig.EncounterTriggerConfig defaults;

        TriggerType(String id, double baseChance, double maxChance, double firstTimeBonus) {
            this.id = id;
            this.defaults = new LorekeeperConfig.EncounterTriggerConfig(baseChance, maxChance, firstTimeBonus);
        }
    }

    private static LorekeeperConfig.EncounterTriggerConfig getTriggerConfig(TriggerType trigger) {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config == null || config.encounterTriggers == null) {
            return trigger.defaults;
        }
        LorekeeperConfig.EncounterTriggerConfig override = config.encounterTriggers.get(trigger.id);
        return override != null ? override : trigger.defaults;
    }

    private static double getNearbyRadius() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.encounterNearbyRadius : DEFAULT_NEARBY_RADIUS;
    }

    private static long getEncounterCooldownTicks() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        int seconds = config != null ? config.encounterCooldownSeconds : DEFAULT_ENCOUNTER_COOLDOWN_SECONDS;
        return Math.max(0, seconds) * 20L;
    }

    private static long getCombatCooldownTicks() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        int seconds = config != null ? config.combatCooldownSeconds : DEFAULT_COMBAT_COOLDOWN_SECONDS;
        return Math.max(0, seconds) * 20L;
    }

    private static long getChunkStayTicks() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        int minutes = config != null ? config.chunkStayMinutes : DEFAULT_CHUNK_STAY_MINUTES;
        return Math.max(0, minutes) * 60L * 20L;
    }

    private static int getDespawnDelayTicks() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        int minutes = config != null ? config.lorekeeperDespawnMinutes : DEFAULT_DESPAWN_MINUTES;
        return Math.max(0, minutes) * 60 * 20;
    }

    private static double getLuckRampStep() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.luckRampStep : DEFAULT_LUCK_RAMP_STEP;
    }

    private static double getFirstEncounterBonus() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.firstEncounterBonus : DEFAULT_FIRST_ENCOUNTER_BONUS;
    }

    private static int getMaxSettlementChunks() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.maxSettlementChunks : DEFAULT_MAX_SETTLEMENT_CHUNKS;
    }
}
