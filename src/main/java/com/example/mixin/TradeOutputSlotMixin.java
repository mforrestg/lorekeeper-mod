package com.example.mixin;

import com.example.LoreBooks;
import com.example.LoreStorage;
import com.example.entity.LorekeeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.TradeOutputSlot;
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
        LoreBooks.BookSubmission submission = LoreBooks.extractWrittenBook(submittedBook);
        if (submission == null || submission.content().trim().isEmpty()) {
            return;
        }

        String title = submission.title().isBlank() ? "Untitled" : submission.title();
        String author = submission.author().isBlank() ? "Unknown" : submission.author();
        String entry = "Book Submission: \"" + title + "\" (by " + author + ")\n" + submission.content();
        storage.addEntryTextChunked(entry, player.getName().getString(), System.currentTimeMillis(), SUBMISSION_CHUNK_LENGTH);
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
}
