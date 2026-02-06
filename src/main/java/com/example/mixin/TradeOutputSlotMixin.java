package com.example.mixin;

import com.example.LoreBooks;
import com.example.LoreStorage;
import com.example.entity.LorekeeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.TradeOutputSlot;
import net.minecraft.text.Text;
import net.minecraft.village.Merchant;
import net.minecraft.village.MerchantInventory;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TradeOutputSlot.class)
public class TradeOutputSlotMixin {
    private static final int SUBMISSION_CHUNK_LENGTH = 500;

    @Shadow @Final private Merchant merchant;
    @Shadow @Final private MerchantInventory merchantInventory;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void lorekeeper$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(merchant instanceof LorekeeperEntity)) {
            return;
        }
        if (!(player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) {
            return;
        }

        LoreStorage storage = LoreStorage.get(serverWorld.getServer());

        TradeOffer offer = merchantInventory.getTradeOffer();
        if (offer == null || !isSubmissionOffer(offer)) {
            return;
        }

        ItemStack submittedBook = findSubmittedBook();
        if (LoreBooks.isLorekeeperBook(submittedBook)) {
            revokePayment(player, stack);
            player.sendMessage(Text.literal("Lore Keeper: I cannot buy my own writings."), false);
            return;
        }
        LoreBooks.BookSubmission submission = LoreBooks.extractWrittenBook(submittedBook);
        if (submission == null || submission.content().trim().isEmpty()) {
            return;
        }

        String title = submission.title().isBlank() ? "Untitled" : submission.title();
        String author = submission.author().isBlank() ? "Unknown" : submission.author();
        String entry = "Book Submission: \"" + title + "\" (by " + author + ")\n" + submission.content();
        storage.addEntryTextChunked(
            entry,
            player.getName().getString(),
            System.currentTimeMillis(),
            SUBMISSION_CHUNK_LENGTH,
            serverWorld.getRegistryKey().getValue().toString(),
            player.getBlockPos().getX(),
            player.getBlockPos().getY(),
            player.getBlockPos().getZ(),
            "book_submission",
            java.util.List.of("book", "submission")
        );
    }

    private boolean isSubmissionOffer(TradeOffer offer) {
        ItemStack sellItem = offer.getSellItem();
        if (!sellItem.isOf(Items.EMERALD) || sellItem.getCount() != 10) {
            return false;
        }
        return offer.getFirstBuyItem().item().value() == Items.WRITTEN_BOOK;
    }

    private ItemStack findSubmittedBook() {
        ItemStack first = merchantInventory.getStack(0);
        if (first.isOf(Items.WRITTEN_BOOK)) {
            return first;
        }
        ItemStack second = merchantInventory.getStack(1);
        if (second.isOf(Items.WRITTEN_BOOK)) {
            return second;
        }
        return ItemStack.EMPTY;
    }

    private void revokePayment(PlayerEntity player, ItemStack outputStack) {
        if (!outputStack.isOf(Items.EMERALD)) {
            return;
        }
        int count = outputStack.getCount();
        outputStack.setCount(0);
        int remaining = count;
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (cursor.isOf(Items.EMERALD)) {
            int removed = Math.min(remaining, cursor.getCount());
            cursor.decrement(removed);
            remaining -= removed;
        }
        if (remaining <= 0) {
            return;
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(Items.EMERALD)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.decrement(removed);
            remaining -= removed;
            if (remaining <= 0) {
                break;
            }
        }
    }
}
