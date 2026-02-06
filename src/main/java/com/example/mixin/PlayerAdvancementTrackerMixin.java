package com.example.mixin;

import com.example.LorekeeperMilestones;
import java.util.List;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
	@Shadow
	private ServerPlayerEntity owner;

	@Shadow
	public abstract AdvancementProgress getProgress(AdvancementEntry advancement);

	@Inject(method = "grantCriterion", at = @At("RETURN"))
	private void lorekeeper$onGrantCriterion(
		AdvancementEntry advancement,
		String criterionName,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValueZ()) {
			return;
		}

		AdvancementProgress progress = getProgress(advancement);
		if (progress == null || !progress.isDone()) {
			return;
		}

		Text titleText = Advancement.getNameFromIdentity(advancement);
		String title = titleText == null ? "" : titleText.getString();
		if (title == null || title.isBlank()) {
			title = advancement.id().toString();
		}

		LorekeeperMilestones.record(
			owner,
			"Achieved advancement: " + title + ".",
			"advancement",
			List.of("advancement", advancement.id().toString())
		);
	}
}
