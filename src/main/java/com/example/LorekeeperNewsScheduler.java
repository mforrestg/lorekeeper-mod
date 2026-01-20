package com.example;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class LorekeeperNewsScheduler {
    private LorekeeperNewsScheduler() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LorekeeperNewsScheduler::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }
        long weekNumber = LoreStorage.getCurrentWeek(world.getServer());
        if (LoreStorage.get(world.getServer()).hasNewsSnapshot(weekNumber)) {
            return;
        }
        LorekeeperNewsPublisher.publishWeeklyNews(world.getServer(), true);
    }
}
