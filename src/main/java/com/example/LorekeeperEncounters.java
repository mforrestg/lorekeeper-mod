package com.example;

import com.example.entity.LorekeeperEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

public final class LorekeeperEncounters {
    private static final double NEARBY_RADIUS = 16.0;
    private static final Text INTRO_LINE_1 =
        Text.literal("Greetings. I am the Lore Keeper, a local historian.");
    private static final Text INTRO_LINE_2 =
        Text.literal("I noticed you put down a crafting table and are working on something.");

    private LorekeeperEncounters() {}

    public static void handleCraftingTablePlaced(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        if (isLorekeeperNearby(world, new Vec3d(player.getX(), player.getY(), player.getZ()))) {
            return;
        }
        LorekeeperEntity lorekeeper = spawnLorekeeper(world, pos);
        if (lorekeeper == null) {
            return;
        }

        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        if (!data.hasEncountered(player.getUuid())) {
            data.markEncountered(player.getUuid());
            player.sendMessage(Text.literal("Lore Keeper: ").append(INTRO_LINE_1), false);
            player.sendMessage(Text.literal("Lore Keeper: ").append(INTRO_LINE_2), false);
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

    private static LorekeeperEntity spawnLorekeeper(ServerWorld world, BlockPos pos) {
        BlockPos spawnPos = pos.up();
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
        world.spawnEntity(lorekeeper);
        return lorekeeper;
    }

    private static boolean isLorekeeperNearby(ServerWorld world, Vec3d center) {
        double radiusSq = NEARBY_RADIUS * NEARBY_RADIUS;
        return !world.getEntitiesByType(
            TypeFilter.instanceOf(LorekeeperEntity.class),
            entity -> entity.squaredDistanceTo(center) <= radiusSq
        ).isEmpty();
    }
}
