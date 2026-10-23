package com.drakonsqoltweaks.client.config;

import net.minecraft.network.chat.Component;

public enum BreedingTimerMode {
	SHARED_RESET_ON_FEED("Shared Timer (Resets on Feed)"),
	PER_ANIMAL_SPECIES("Per Animal Species");

	private final String label;

	BreedingTimerMode(String label) {
		this.label = label;
	}

	public Component displayName() {
		return Component.literal(this.label);
	}
}
