package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class HotbarRefillManager {
	private static final HotbarRefillManager INSTANCE = new HotbarRefillManager();

	private final ItemStack[] previousHotbar = new ItemStack[9];
	private ItemStack previousOffhand = ItemStack.EMPTY;
	private int lastSelectedSlot = -1;
	private long lastDropTick = -100L;

	private HotbarRefillManager() {
		resetSnapshots();
	}

	public static HotbarRefillManager getInstance() {
		return INSTANCE;
	}

	public void resetSnapshots() {
		for (int i = 0; i < 9; i++) {
			this.previousHotbar[i] = ItemStack.EMPTY;
		}
		this.previousOffhand = ItemStack.EMPTY;
		this.lastSelectedSlot = -1;
	}

	public void onPlayerDrop(LocalPlayer player) {
		if (player != null && player.level() != null) {
			this.lastDropTick = player.level().getGameTime();
		}
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || client.level == null) {
			resetSnapshots();
			return;
		}

		Inventory inventory = player.getInventory();
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();

		// Do not auto-refill while a GUI screen is open or in Spectator mode; just keep snapshots updated
		if (!config.isHotbarRefillEnabled() || client.gui.screen() != null || player.isSpectator()) {
			captureSnapshots(inventory);
			return;
		}

		boolean droppedThisTick = (client.level.getGameTime() - this.lastDropTick) <= 1L;
		if (droppedThisTick && !config.isRefillOnDrop()) {
			captureSnapshots(inventory);
			return;
		}

		int currentSelected = inventory.getSelectedSlot();
		ItemStack currentOffhand = inventory.getItem(Inventory.SLOT_OFFHAND);

		// Check if the player just pressed F to swap mainhand and offhand
		if (currentSelected >= 0 && currentSelected < 9) {
			ItemStack prevMain = this.previousHotbar[currentSelected];
			ItemStack currMain = inventory.getItem(currentSelected);
			if (swappedBetweenHands(prevMain, this.previousOffhand, currMain, currentOffhand)) {
				captureSnapshots(inventory);
				return;
			}
		}

		// 1. Check all 9 hotbar slots (prioritizing the currently selected slot first)
		if (currentSelected >= 0 && currentSelected < 9) {
			checkAndRefillSlot(client, player, inventory, config, currentSelected);
		}
		for (int slot = 0; slot < 9; slot++) {
			if (slot != currentSelected) {
				checkAndRefillSlot(client, player, inventory, config, slot);
			}
		}

		// 2. Check offhand slot if enabled
		if (config.isRefillOffhand()) {
			ItemStack latestOffhand = inventory.getItem(Inventory.SLOT_OFFHAND);
			if (!this.previousOffhand.isEmpty() && latestOffhand.isEmpty()) {
				if (config.getRefillFilterMode().matches(this.previousOffhand)) {
					int sourceSlot = findMatchingInventorySlot(inventory, this.previousOffhand, config.isRefillMatchComponents());
					if (sourceSlot != -1) {
						ItemStack refilledCopy = inventory.getItem(sourceSlot).copy();
						client.gameMode.handleContainerInput(
							player.inventoryMenu.containerId,
							sourceSlot,
							Inventory.SLOT_OFFHAND,
							ContainerInput.SWAP,
							player
						);
						if (config.isNotifyOnRefill()) {
							notifyRefill(player, refilledCopy);
						}
					}
				}
			}
		}

		captureSnapshots(inventory);
	}

	private void checkAndRefillSlot(
		Minecraft client,
		LocalPlayer player,
		Inventory inventory,
		DrakonsQolConfig config,
		int hotbarSlot
	) {
		ItemStack prev = this.previousHotbar[hotbarSlot];
		ItemStack curr = inventory.getItem(hotbarSlot);

		if (!prev.isEmpty() && curr.isEmpty()) {
			if (!config.getRefillFilterMode().matches(prev)) {
				return;
			}
			int sourceSlot = findMatchingInventorySlot(inventory, prev, config.isRefillMatchComponents());
			if (sourceSlot != -1) {
				ItemStack refilledCopy = inventory.getItem(sourceSlot).copy();
				client.gameMode.handleContainerInput(
					player.inventoryMenu.containerId,
					sourceSlot,
					hotbarSlot,
					ContainerInput.SWAP,
					player
				);
				if (config.isNotifyOnRefill()) {
					notifyRefill(player, refilledCopy);
				}
			}
		}
	}

	private int findMatchingInventorySlot(Inventory inventory, ItemStack target, boolean matchComponents) {
		int bestSlot = -1;
		int bestCount = 0;

		// Pass 1: Exact item + components match in main inventory (slots 9..35)
		for (int i = 9; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
				if (stack.getCount() > bestCount) {
					bestCount = stack.getCount();
					bestSlot = i;
				}
			}
		}

		if (bestSlot != -1 || matchComponents) {
			return bestSlot;
		}

		// Pass 2: Same item type (ignoring components) if matchComponents is false
		for (int i = 9; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.is(target.getItem())) {
				if (stack.getCount() > bestCount) {
					bestCount = stack.getCount();
					bestSlot = i;
				}
			}
		}

		return bestSlot;
	}

	private boolean swappedBetweenHands(ItemStack prevMain, ItemStack prevOff, ItemStack currMain, ItemStack currOff) {
		if (!prevMain.isEmpty() && prevOff.isEmpty() && currMain.isEmpty() && !currOff.isEmpty()) {
			return ItemStack.isSameItemSameComponents(prevMain, currOff) && prevMain.getCount() == currOff.getCount();
		}
		if (prevMain.isEmpty() && !prevOff.isEmpty() && !currMain.isEmpty() && currOff.isEmpty()) {
			return ItemStack.isSameItemSameComponents(prevOff, currMain) && prevOff.getCount() == currMain.getCount();
		}
		return false;
	}

	private void captureSnapshots(Inventory inventory) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			this.previousHotbar[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
		}
		ItemStack off = inventory.getItem(Inventory.SLOT_OFFHAND);
		this.previousOffhand = off.isEmpty() ? ItemStack.EMPTY : off.copy();
		this.lastSelectedSlot = inventory.getSelectedSlot();
	}

	private void notifyRefill(LocalPlayer player, ItemStack refilledStack) {
		Component msg = ModMessageUtil.createPrefix().append(
			Component.literal("Refilled ")
				.withStyle(ChatFormatting.GRAY)
				.append(Component.literal(refilledStack.getCount() + "x ").withStyle(ChatFormatting.AQUA))
				.append(refilledStack.getHoverName().copy().withStyle(ChatFormatting.WHITE))
		);
		player.sendOverlayMessage(msg);
	}
}
