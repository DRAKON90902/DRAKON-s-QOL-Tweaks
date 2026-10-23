package com.drakonsqoltweaks.client.config;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public enum RefillFilterMode {
	ALL_ITEMS("All Items"),
	BLOCKS_ONLY("Blocks Only"),
	BLOCKS_AND_CONSUMABLES("Blocks & Food");

	private final String label;

	RefillFilterMode(String label) {
		this.label = label;
	}

	public Component displayName() {
		return Component.literal(this.label);
	}

	public boolean matches(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return switch (this) {
			case ALL_ITEMS -> true;
			case BLOCKS_ONLY -> stack.getItem() instanceof BlockItem;
			case BLOCKS_AND_CONSUMABLES -> stack.getItem() instanceof BlockItem || stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE);
		};
	}
}
