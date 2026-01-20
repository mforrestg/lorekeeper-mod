package com.example.mixin;

import com.example.LorekeeperEncounters;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void lorekeeper$onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted()) {
            return;
        }
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return;
        }

        LorekeeperEncounters.handleBlockPlaced(serverWorld, player, context.getBlockPos(), getBlock());
    }
}
