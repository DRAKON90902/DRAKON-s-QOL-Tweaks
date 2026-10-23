package com.drakonsqoltweaks.client.mixin;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.feature.BreedingTimerManager;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	private boolean isHidden;

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void drakonsqoltweaks$renderActiveTimersOverlay(
		GuiGraphicsExtractor graphics,
		DeltaTracker deltaTracker,
		CallbackInfo ci
	) {
		if (this.isHidden || this.minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isShowActiveTimersOnHud()) {
			return;
		}

		Collection<BreedingTimerManager.BreedingTimerEntry> breedingTimers =
			config.isBreedingTimerEnabled() ? BreedingTimerManager.getInstance().getActiveTimers() : List.of();
		Collection<FurnaceTimerManager.FurnaceTimerEntry> furnaceTimers =
			config.isFurnaceTimerEnabled() ? FurnaceTimerManager.getInstance().getActiveTimers() : List.of();

		if (breedingTimers.isEmpty() && furnaceTimers.isEmpty()) {
			return;
		}

		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		for (BreedingTimerManager.BreedingTimerEntry entry : breedingTimers) {
			String timeStr = ModMessageUtil.formatTicksAsMinutesSeconds(entry.remainingTicks());
			lines.add("Breeding (" + entry.animalName() + "): " + timeStr);
			colors.add(0xFFFF88CC);
		}

		for (FurnaceTimerManager.FurnaceTimerEntry entry : furnaceTimers) {
			String timeStr = ModMessageUtil.formatTicksAsMinutesSeconds(entry.remainingTicks());
			String suffix = entry.fuelRunsOutFirst() ? " [Fuel]" : "";
			lines.add(entry.furnaceType() + " (" + entry.itemCount() + "x " + entry.itemName() + "): " + timeStr + suffix);
			colors.add(entry.fuelRunsOutFirst() ? 0xFFFFAA00 : 0xFF88FF88);
		}

		Font font = this.minecraft.font;
		int x = 6;
		int y = 6;
		int maxWidth = 0;
		for (String line : lines) {
			maxWidth = Math.max(maxWidth, font.width(line));
		}

		int boxHeight = lines.size() * 11 + 4;
		graphics.fill(x - 3, y - 3, x + maxWidth + 4, y + boxHeight - 2, 0x66000000);

		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x, y + (i * 11), colors.get(i), true);
		}
	}
}
