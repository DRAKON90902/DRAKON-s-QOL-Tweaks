package com.drakonsqoltweaks.client.config;

import net.minecraft.network.chat.Component;

public enum MessageDisplayMode {
	CHAT_ONLY("Chat Only"),
	ACTIONBAR_ONLY("Actionbar Only"),
	CHAT_AND_ACTIONBAR("Chat & Actionbar");

	private final String label;

	MessageDisplayMode(String label) {
		this.label = label;
	}

	public Component displayName() {
		return Component.literal(this.label);
	}

	public boolean sendToChat() {
		return this == CHAT_ONLY || this == CHAT_AND_ACTIONBAR;
	}

	public boolean sendToActionbar() {
		return this == ACTIONBAR_ONLY || this == CHAT_AND_ACTIONBAR;
	}
}
