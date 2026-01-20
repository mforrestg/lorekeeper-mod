package com.example.client;

import com.example.LorekeeperMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.WanderingTraderEntityRenderer;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.util.Identifier;

public class LorekeeperEntityRenderer extends WanderingTraderEntityRenderer {
    private static final Identifier TEXTURE =
        Identifier.of(LorekeeperMod.MOD_ID, "textures/entity/lorekeeper.png");

    public LorekeeperEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(VillagerEntityRenderState state) {
        return TEXTURE;
    }
}
