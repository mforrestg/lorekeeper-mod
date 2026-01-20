package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;

public class LorekeeperClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(LorekeeperEntities.LOREKEEPER, WanderingTraderEntityRenderer::new);
    }
}
