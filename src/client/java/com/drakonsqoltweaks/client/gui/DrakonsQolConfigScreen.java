package com.drakonsqoltweaks.client.gui;

import com.drakonsqoltweaks.client.config.BreedingTimerMode;
import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.MessageDisplayMode;
import com.drakonsqoltweaks.client.config.QolFeatureRegistry;
import com.drakonsqoltweaks.client.config.RefillFilterMode;
import com.drakonsqoltweaks.client.config.StructureReturnMode;
import com.drakonsqoltweaks.client.config.TntTimerFormat;
import com.drakonsqoltweaks.client.feature.AdminActionHandler;
import com.drakonsqoltweaks.client.feature.BreedingTimerManager;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

public class DrakonsQolConfigScreen extends Screen {
	public enum ConfigTab {
		REFILL("drakonsqoltweaks.tab.refill"),
		TIMERS("drakonsqoltweaks.tab.timers"),
		MESSAGES("drakonsqoltweaks.tab.messages"),
		EXTRA("drakonsqoltweaks.tab.extra");

		private final String translationKey;

		ConfigTab(String translationKey) {
			this.translationKey = translationKey;
		}

		public Component title() {
			return Component.translatable(this.translationKey);
		}
	}

	private final Screen lastScreen;
	private ConfigTab activeTab = ConfigTab.REFILL;

	public DrakonsQolConfigScreen(Screen lastScreen) {
		super(Component.translatable("drakonsqoltweaks.title"));
		this.lastScreen = lastScreen;
	}

	@Override
	protected void init() {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();

		int margin = 16;
		int totalWidth = this.width - (margin * 2);
		int tabGap = 4;
		ConfigTab[] tabs = ConfigTab.values();
		int tabWidth = (totalWidth - (tabGap * (tabs.length - 1))) / tabs.length;
		int tabY = 28;

		for (int i = 0; i < tabs.length; i++) {
			ConfigTab tab = tabs[i];
			int x = margin + i * (tabWidth + tabGap);
			int w = (i == tabs.length - 1) ? (this.width - margin - x) : tabWidth;
			Component label = (tab == this.activeTab)
				? Component.literal("> ").append(tab.title()).append(Component.literal(" <")).withStyle(ChatFormatting.YELLOW)
				: tab.title();
			this.addRenderableWidget(Button.builder(label, btn -> switchTab(tab))
				.bounds(x, tabY, w, 20)
				.build());
		}

		int contentTop = 58;
		int colGap = 8;
		int colWidth = Math.min(210, (totalWidth - colGap) / 2);
		int leftColX = (this.width / 2) - colWidth - (colGap / 2);
		int rightColX = (this.width / 2) + (colGap / 2);
		int fullRowWidth = (colWidth * 2) + colGap;

		switch (this.activeTab) {
			case REFILL -> initRefillTab(config, leftColX, rightColX, colWidth, contentTop);
			case TIMERS -> initTimersTab(config, leftColX, rightColX, colWidth, contentTop);
			case MESSAGES -> initMessagesTab(config, leftColX, rightColX, colWidth, fullRowWidth, contentTop);
			case EXTRA -> initExtraTab(config, leftColX, rightColX, colWidth, fullRowWidth, contentTop);
		}

		// Bottom Footer Controls: Clear Timers, Reset Defaults, Done
		int footerY = this.height - 28;
		int footerBtnWidth = (fullRowWidth - (colGap * 2)) / 3;

		this.addRenderableWidget(Button.builder(
			Component.translatable("drakonsqoltweaks.button.clear_timers"),
			btn -> {
				BreedingTimerManager.getInstance().clearAll();
				FurnaceTimerManager.getInstance().clearAll();
				this.rebuildWidgets();
			}
		).bounds(leftColX, footerY, footerBtnWidth, 20)
			.tooltip(Tooltip.create(Component.literal("Clear all currently running animal breeding and furnace timers")))
			.build());

		this.addRenderableWidget(Button.builder(
			Component.translatable("drakonsqoltweaks.button.reset_defaults"),
			btn -> {
				config.resetToDefaults();
				this.rebuildWidgets();
			}
		).bounds(leftColX + footerBtnWidth + colGap, footerY, footerBtnWidth, 20)
			.tooltip(Tooltip.create(Component.literal("Reset all Drakon's QOL Tweaks settings to their default values")))
			.build());

		int doneX = leftColX + (footerBtnWidth + colGap) * 2;
		int doneW = (leftColX + fullRowWidth) - doneX;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
			.bounds(doneX, footerY, doneW, 20)
			.build());
	}

	private void initRefillTab(DrakonsQolConfig config, int leftX, int rightX, int colW, int startY) {
		int rowStep = 26;

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isHotbarRefillEnabled())
				.withTooltip(val -> Tooltip.create(Component.literal("Automatically refill hotbar stacks from your inventory when they run out")))
				.create(leftX, startY, colW, 20, Component.literal("Auto Hotbar Refill"), (btn, val) -> {
					config.setHotbarRefillEnabled(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.builder(RefillFilterMode::displayName, config.getRefillFilterMode())
				.withValues(RefillFilterMode.values())
				.withTooltip(val -> Tooltip.create(Component.literal("Choose whether to refill all items, blocks only, or blocks and food")))
				.create(rightX, startY, colW, 20, Component.literal("Refill Filter"), (btn, val) -> {
					config.setRefillFilterMode(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isRefillOffhand())
				.withTooltip(val -> Tooltip.create(Component.literal("Also automatically refill the offhand slot when its item stack runs out")))
				.create(leftX, startY + rowStep, colW, 20, Component.literal("Refill Offhand"), (btn, val) -> {
					config.setRefillOffhand(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isRefillMatchComponents())
				.withTooltip(val -> Tooltip.create(Component.literal("Require replacement items to have matching components (custom names, enchantments, etc.)")))
				.create(rightX, startY + rowStep, colW, 20, Component.literal("Match Components"), (btn, val) -> {
					config.setRefillMatchComponents(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isRefillOnDrop())
				.withTooltip(val -> Tooltip.create(Component.literal("Refill slot even when intentionally dropping the entire stack with Q")))
				.create(leftX, startY + rowStep * 2, colW, 20, Component.literal("Refill on Drop (Q)"), (btn, val) -> {
					config.setRefillOnDrop(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isNotifyOnRefill())
				.withTooltip(val -> Tooltip.create(Component.literal("Show a subtle actionbar notification when a hotbar stack is refilled")))
				.create(rightX, startY + rowStep * 2, colW, 20, Component.literal("Refill Notification"), (btn, val) -> {
					config.setNotifyOnRefill(val);
					config.save();
				})
		);
	}

	private void initTimersTab(DrakonsQolConfig config, int leftX, int rightX, int colW, int startY) {
		int rowStep = 24;

		// Row 0: TNT Timer Enable & TNT Format
		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isTntTimerEnabled())
				.withTooltip(val -> Tooltip.create(Component.literal("Display a floating countdown timer above lit TNT entities")))
				.create(leftX, startY, colW, 20, Component.literal("Lit TNT Timer"), (btn, val) -> {
					config.setTntTimerEnabled(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.builder(TntTimerFormat::displayName, config.getTntTimerFormat())
				.withValues(TntTimerFormat.values())
				.withTooltip(val -> Tooltip.create(Component.literal("Format for the floating TNT countdown timer (seconds & milliseconds)")))
				.create(rightX, startY, colW, 20, Component.literal("TNT Format"), (btn, val) -> {
					config.setTntTimerFormat(val);
					config.save();
				})
		);

		// Row 1: TNT Dynamic Color & HUD Active Timers
		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isTntDynamicColor())
				.withTooltip(val -> Tooltip.create(Component.literal("Color-code the TNT countdown from green to yellow to red as it nears explosion")))
				.create(leftX, startY + rowStep, colW, 20, Component.literal("TNT Dynamic Color"), (btn, val) -> {
					config.setTntDynamicColor(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isShowActiveTimersOnHud())
				.withTooltip(val -> Tooltip.create(Component.literal("Display active background Breeding and Furnace timers in the top-left HUD corner")))
				.create(rightX, startY + rowStep, colW, 20, Component.literal("HUD Timer Overlay"), (btn, val) -> {
					config.setShowActiveTimersOnHud(val);
					config.save();
				})
		);

		// Row 2: Animal Breeding Timer Enable & Breeding Timer Mode
		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isBreedingTimerEnabled())
				.withTooltip(val -> Tooltip.create(Component.literal("Start a 5-minute background cooldown timer when feeding animals to breed")))
				.create(leftX, startY + rowStep * 2, colW, 20, Component.literal("Breeding Timer"), (btn, val) -> {
					config.setBreedingTimerEnabled(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.builder(BreedingTimerMode::displayName, config.getBreedingTimerMode())
				.withValues(BreedingTimerMode.values())
				.withTooltip(val -> Tooltip.create(Component.literal("Reset a single shared 5m timer when any animal is fed, or track per species")))
				.create(rightX, startY + rowStep * 2, colW, 20, Component.literal("Breeding Mode"), (btn, val) -> {
					config.setBreedingTimerMode(val);
					config.save();
				})
		);

		// Row 3: Breeding Cooldown Duration Slider & Furnace Timer Enable
		this.addRenderableWidget(new BreedingCooldownSlider(leftX, startY + rowStep * 3, colW, 20, config));

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isFurnaceTimerEnabled())
				.withTooltip(val -> Tooltip.create(Component.literal("Track furnace, blast furnace, and smoker smelting/fuel time in the background")))
				.create(rightX, startY + rowStep * 3, colW, 20, Component.literal("Furnace Timer"), (btn, val) -> {
					config.setFurnaceTimerEnabled(val);
					config.save();
				})
		);

		// Row 4: Show Timer inside Furnace GUI
		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isShowTimerInFurnaceGui())
				.withTooltip(val -> Tooltip.create(Component.literal("Show the remaining smelt/fuel time directly inside the Furnace GUI screen")))
				.create(leftX, startY + rowStep * 4, colW, 20, Component.literal("Furnace GUI Readout"), (btn, val) -> {
					config.setShowTimerInFurnaceGui(val);
					config.save();
				})
		);
	}

	private void initMessagesTab(DrakonsQolConfig config, int leftX, int rightX, int colW, int fullW, int startY) {
		int rowStep = 24;

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isModMessagesEnabled())
				.withTooltip(val -> Tooltip.create(Component.literal("Enable client-only chat/actionbar alerts when timers finish")))
				.create(leftX, startY, colW, 20, Component.literal("Mod Alerts Enabled"), (btn, val) -> {
					config.setModMessagesEnabled(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.builder(MessageDisplayMode::displayName, config.getMessageDisplayMode())
				.withValues(MessageDisplayMode.values())
				.withTooltip(val -> Tooltip.create(Component.literal("Where client-only timer completion messages should appear")))
				.create(rightX, startY, colW, 20, Component.literal("Alert Location"), (btn, val) -> {
					config.setMessageDisplayMode(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isShowModPrefix())
				.withTooltip(val -> Tooltip.create(Component.literal("Include the [Drakon's QOL] prefix before alert messages")))
				.create(leftX, startY + rowStep, colW, 20, Component.literal("Show [Drakon's QOL]"), (btn, val) -> {
					config.setShowModPrefix(val);
					config.save();
				})
		);

		this.addRenderableWidget(
			CycleButton.onOffBuilder(config.isPlayAlertSound())
				.withTooltip(val -> Tooltip.create(Component.literal("Play a client-side chime sound when a timer completes")))
				.create(rightX, startY + rowStep, colW, 20, Component.literal("Play Alert Sound"), (btn, val) -> {
					config.setPlayAlertSound(val);
					config.save();
				})
		);

		int boxY = startY + rowStep * 2 + 10;
		EditBox breedingMsgBox = new EditBox(this.font, leftX, boxY, fullW, 18, Component.literal("Breeding Ready Message"));
		breedingMsgBox.setMaxLength(160);
		breedingMsgBox.setValue(config.getBreedingReadyMessage());
		breedingMsgBox.setResponder(val -> {
			config.setBreedingReadyMessage(val);
			config.save();
		});
		breedingMsgBox.setTooltip(Tooltip.create(Component.literal("Breeding Ready Message. Placeholders: {animal}")));
		this.addRenderableWidget(breedingMsgBox);

		EditBox furnaceDoneBox = new EditBox(this.font, leftX, boxY + 30, fullW, 18, Component.literal("Furnace Done Message"));
		furnaceDoneBox.setMaxLength(160);
		furnaceDoneBox.setValue(config.getFurnaceDoneMessage());
		furnaceDoneBox.setResponder(val -> {
			config.setFurnaceDoneMessage(val);
			config.save();
		});
		furnaceDoneBox.setTooltip(Tooltip.create(Component.literal("Furnace Smelting Complete Message. Placeholders: {furnace}, {count}, {item}, {items}")));
		this.addRenderableWidget(furnaceDoneBox);

		EditBox furnaceFuelOutBox = new EditBox(this.font, leftX, boxY + 60, fullW, 18, Component.literal("Furnace Fuel Out Message"));
		furnaceFuelOutBox.setMaxLength(160);
		furnaceFuelOutBox.setValue(config.getFurnaceFuelOutMessage());
		furnaceFuelOutBox.setResponder(val -> {
			config.setFurnaceFuelOutMessage(val);
			config.save();
		});
		furnaceFuelOutBox.setTooltip(Tooltip.create(Component.literal("Furnace Out of Fuel Message. Placeholders: {furnace}, {count}, {item}, {items}")));
		this.addRenderableWidget(furnaceFuelOutBox);
	}

	private void initExtraTab(DrakonsQolConfig config, int leftX, int rightX, int colW, int fullW, int startY) {
		List<QolFeatureRegistry.FeatureEntry> extraFeatures = QolFeatureRegistry.getRegisteredFeatures();
		int y = startY;
		for (int i = 0; i < extraFeatures.size(); i++) {
			QolFeatureRegistry.FeatureEntry entry = extraFeatures.get(i);
			int x = (i % 2 == 0) ? leftX : rightX;
			int rowY = y + (i / 2) * 24;
			this.addRenderableWidget(
				CycleButton.onOffBuilder(entry.getter().getAsBoolean())
					.withTooltip(val -> Tooltip.create(Component.literal(entry.description())))
					.create(x, rowY, colW, 20, Component.literal(entry.displayName()), (btn, val) -> entry.setter().accept(val))
			);
		}

		int structRowY = startY + ((extraFeatures.size() + 1) / 2) * 24 + 4;
		this.addRenderableWidget(
			CycleButton.builder(StructureReturnMode::displayName, config.getDefaultStructureReturnMode())
				.withValues(StructureReturnMode.values())
				.withTooltip(val -> Tooltip.create(Component.literal("Default output format for /dqol s when return determinant is omitted")))
				.create(leftX, structRowY, colW, 20, Component.literal("/dqol s Output"), (btn, val) -> {
					config.setDefaultStructureReturnMode(val);
					config.save();
				})
		);

		this.addRenderableWidget(new StructureMaxDistanceSlider(rightX, structRowY, colW, 20, config));

		int toolsRowY = structRowY + 24;
		int quarterW = (fullW - 6) / 4;
		this.addRenderableWidget(Button.builder(
			Component.literal("Enchant UI"),
			btn -> {
				if (AdminActionHandler.checkPermissionOrNotify(this.minecraft)) {
					this.minecraft.gui.setScreen(new DrakonsEnchantScreen(this));
				}
			}
		).bounds(leftX, toolsRowY, quarterW, 20)
			.tooltip(Tooltip.create(Component.literal("Open /dqol enchant (SkyBlock-style Enchanter)")))
			.build());

		this.addRenderableWidget(Button.builder(
			Component.literal("Anvil UI"),
			btn -> AdminActionHandler.openAnvil(this.minecraft)
		).bounds(leftX + quarterW + 2, toolsRowY, quarterW, 20)
			.tooltip(Tooltip.create(Component.literal("Open /dqol anvil")))
			.build());

		this.addRenderableWidget(Button.builder(
			Component.literal("Craft UI"),
			btn -> AdminActionHandler.openCraftingTable(this.minecraft)
		).bounds(leftX + (quarterW + 2) * 2, toolsRowY, quarterW, 20)
			.tooltip(Tooltip.create(Component.literal("Open /dqol craft")))
			.build());

		this.addRenderableWidget(Button.builder(
			Component.literal("Summon UI"),
			btn -> {
				if (AdminActionHandler.checkPermissionOrNotify(this.minecraft)) {
					this.minecraft.gui.setScreen(new DrakonsSummonScreen(this));
				}
			}
		).bounds(leftX + (quarterW + 2) * 3, toolsRowY, fullW - (quarterW + 2) * 3, 20)
			.tooltip(Tooltip.create(Component.literal("Open /dqol summon (Entity Spawner & Attribute Editor)")))
			.build());

		int testRowY = toolsRowY + 24;
		this.addRenderableWidget(Button.builder(
			Component.literal("Start Test 5m Breeding Timer"),
			btn -> BreedingTimerManager.getInstance().startOrResetTimer("Cow")
		).bounds(leftX, testRowY, colW, 20)
			.tooltip(Tooltip.create(Component.literal("Start or reset the 5-minute background breeding timer for testing")))
			.build());

		this.addRenderableWidget(Button.builder(
			Component.literal("Send Test Alert Message"),
			btn -> ModMessageUtil.sendAlert(Component.literal("Test notification from Drakon's QOL Tweaks!"), false)
		).bounds(rightX, testRowY, colW, 20)
			.tooltip(Tooltip.create(Component.literal("Send a sample client-side alert message to chat/actionbar")))
			.build());
	}

	private void switchTab(ConfigTab tab) {
		if (this.activeTab != tab) {
			this.activeTab = tab;
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

		int margin = 16;
		int totalWidth = this.width - (margin * 2);
		int colGap = 8;
		int colWidth = Math.min(210, (totalWidth - colGap) / 2);
		int leftColX = (this.width / 2) - colWidth - (colGap / 2);

		if (this.activeTab == ConfigTab.MESSAGES) {
			int baseBoxY = 58 + 24 * 2 + 10;
			graphics.text(this.font, Component.literal("Breeding Ready Alert ({animal}):"), leftColX, baseBoxY - 10, 0xFFCCCCCC);
			graphics.text(this.font, Component.literal("Furnace Smelting Complete Alert ({furnace}, {count}, {item}):"), leftColX, baseBoxY + 20, 0xFFCCCCCC);
			graphics.text(this.font, Component.literal("Furnace Fuel Ran Out Alert ({furnace}, {item}):"), leftColX, baseBoxY + 50, 0xFFCCCCCC);
		} else if (this.activeTab == ConfigTab.EXTRA) {
			List<QolFeatureRegistry.FeatureEntry> extraFeatures = QolFeatureRegistry.getRegisteredFeatures();
			int statusTop = 58 + ((extraFeatures.size() + 1) / 2) * 24 + 80;
			graphics.text(this.font, Component.literal("Active Background Timers:").withStyle(ChatFormatting.GOLD), leftColX, statusTop, 0xFFFFAA00);

			int lineY = statusTop + 13;
			Collection<BreedingTimerManager.BreedingTimerEntry> breedingTimers = BreedingTimerManager.getInstance().getActiveTimers();
			Collection<FurnaceTimerManager.FurnaceTimerEntry> furnaceTimers = FurnaceTimerManager.getInstance().getActiveTimers();

			if (breedingTimers.isEmpty() && furnaceTimers.isEmpty()) {
				graphics.text(this.font, Component.literal("No active breeding or furnace timers running."), leftColX, lineY, 0xFF888888);
			} else {
				for (BreedingTimerManager.BreedingTimerEntry b : breedingTimers) {
					String line = "• Breeding (" + b.animalName() + "): " + ModMessageUtil.formatTicksAsMinutesSeconds(b.remainingTicks()) + " remaining";
					graphics.text(this.font, Component.literal(line), leftColX, lineY, 0xFFFF88CC);
					lineY += 11;
				}
				for (FurnaceTimerManager.FurnaceTimerEntry f : furnaceTimers) {
					String status = f.fuelRunsOutFirst() ? "fuel runs out in " : "done smelting in ";
					String line = "• " + f.furnaceType() + " (" + f.itemCount() + "x " + f.itemName() + "): "
						+ status + ModMessageUtil.formatTicksAsMinutesSeconds(f.remainingTicks());
					graphics.text(this.font, Component.literal(line), leftColX, lineY, f.fuelRunsOutFirst() ? 0xFFFFAA00 : 0xFF88FF88);
					lineY += 11;
				}
			}
		}
	}

	@Override
	public void onClose() {
		DrakonsQolConfig.getInstance().save();
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	private static class BreedingCooldownSlider extends AbstractSliderButton {
		private static final int MIN_SECONDS = 30;
		private static final int MAX_SECONDS = 600;
		private final DrakonsQolConfig config;

		public BreedingCooldownSlider(int x, int y, int width, int height, DrakonsQolConfig config) {
			super(
				x,
				y,
				width,
				height,
				Component.empty(),
				(double) (Math.clamp(config.getBreedingCooldownSeconds(), MIN_SECONDS, MAX_SECONDS) - MIN_SECONDS) / (double) (MAX_SECONDS - MIN_SECONDS)
			);
			this.config = config;
			this.updateMessage();
		}

		private int currentSeconds() {
			int raw = MIN_SECONDS + (int) Math.round(this.value * (MAX_SECONDS - MIN_SECONDS));
			return Math.clamp((raw / 5) * 5, MIN_SECONDS, MAX_SECONDS);
		}

		@Override
		protected void updateMessage() {
			int secs = currentSeconds();
			String formatted = ModMessageUtil.formatTicksAsMinutesSeconds(secs * 20);
			this.setMessage(Component.literal("Breed Cooldown: " + formatted));
		}

		@Override
		protected void applyValue() {
			this.config.setBreedingCooldownSeconds(currentSeconds());
			this.config.save();
		}
	}

	private static class StructureMaxDistanceSlider extends AbstractSliderButton {
		private static final int MIN_DIST = 500;
		private static final int MAX_DIST = 25000;
		private final DrakonsQolConfig config;

		public StructureMaxDistanceSlider(int x, int y, int width, int height, DrakonsQolConfig config) {
			super(
				x,
				y,
				width,
				height,
				Component.empty(),
				(double) (Math.clamp(config.getDefaultStructureMaxDistance(), MIN_DIST, MAX_DIST) - MIN_DIST) / (double) (MAX_DIST - MIN_DIST)
			);
			this.config = config;
			this.updateMessage();
		}

		private int currentDistance() {
			int raw = MIN_DIST + (int) Math.round(this.value * (MAX_DIST - MIN_DIST));
			return Math.clamp((raw / 250) * 250, MIN_DIST, MAX_DIST);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal("Default Max Dist: " + currentDistance() + "m"));
		}

		@Override
		protected void applyValue() {
			this.config.setDefaultStructureMaxDistance(currentDistance());
			this.config.save();
		}
	}
}
