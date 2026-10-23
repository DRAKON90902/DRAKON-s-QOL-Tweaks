package com.drakonsqoltweaks.client.mixin;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceScreen.class)
public abstract class AbstractFurnaceScreenMixin<T extends AbstractFurnaceMenu> extends AbstractRecipeBookScreen<T> {
	protected AbstractFurnaceScreenMixin(
		T menu,
		RecipeBookComponent<?> recipeBookComponent,
		Inventory playerInventory,
		Component title
	) {
		super(menu, recipeBookComponent, playerInventory, title);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void drakonsqoltweaks$renderFurnaceTimer(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float partialTick,
		CallbackInfo ci
	) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isFurnaceTimerEnabled() || !config.isShowTimerInFurnaceGui()) {
			return;
		}

		FurnaceTimerManager.FurnaceTimerEntry entry = FurnaceTimerManager.getInstance().getEntryForOpenMenu(this.menu);
		if (entry == null || entry.remainingTicks() <= 0) {
			return;
		}

		String timeFormatted = ModMessageUtil.formatTicksAsMinutesSeconds(entry.remainingTicks());
		String label = (entry.fuelRunsOutFirst() ? "Fuel Left: " : "Done in: ") + timeFormatted;
		int color = entry.fuelRunsOutFirst() ? 0xFFFFAA00 : 0xFF55FF55;

		int textX = this.leftPos + this.imageWidth - this.font.width(label) - 7;
		int textY = this.topPos + 6;
		graphics.text(this.font, Component.literal(label), textX, textY, color, false);
	}
}
