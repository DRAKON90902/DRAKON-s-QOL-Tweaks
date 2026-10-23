package com.drakonsqoltweaks.client.config;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum TntTimerFormat {
	SECONDS_AND_MILLISECONDS("Sec & Millis (3.850s)"),
	SECONDS_AND_CENTISECONDS("Sec & Centis (3.85s)");

	private final String label;

	TntTimerFormat(String label) {
		this.label = label;
	}

	public Component displayName() {
		return Component.literal(this.label);
	}

	public String formatTicks(float remainingTicks) {
		float clampedTicks = Math.max(0.0F, remainingTicks);
		int totalMillis = Math.max(0, Math.round((clampedTicks / 20.0F) * 1000.0F));
		int seconds = totalMillis / 1000;
		int millis = totalMillis % 1000;
		return switch (this) {
			case SECONDS_AND_MILLISECONDS -> String.format(Locale.ROOT, "%d.%03ds", seconds, millis);
			case SECONDS_AND_CENTISECONDS -> String.format(Locale.ROOT, "%d.%02ds", seconds, millis / 10);
		};
	}
}
