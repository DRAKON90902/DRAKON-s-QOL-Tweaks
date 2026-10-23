package com.drakonsqoltweaks.client.config;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum StructureReturnMode {
	JOURNEYMAP("JourneyMap Waypoint"),
	RAW_COORDINATES("Raw Coordinates");

	private final String label;

	StructureReturnMode(String label) {
		this.label = label;
	}

	public Component displayName() {
		return Component.literal(this.label);
	}

	public static StructureReturnMode fromInput(String input, StructureReturnMode fallback) {
		if (input == null || input.isBlank()) {
			return fallback;
		}
		String normalized = input.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "journeymap", "jm", "waypoint", "wp", "map" -> JOURNEYMAP;
			case "raw", "coords", "coordinates", "coord", "chat", "text" -> RAW_COORDINATES;
			default -> null;
		};
	}
}
