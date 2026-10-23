package com.drakonsqoltweaks.client.util;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.MessageDisplayMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public final class ModMessageUtil {
	private ModMessageUtil() {
	}

	public static MutableComponent createPrefix() {
		return Component.literal("[")
			.withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal("Drakon's QOL").withStyle(ChatFormatting.GOLD))
			.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	public static void sendAlert(Component body, boolean isWarning) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isModMessagesEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		ChatFormatting textColor = isWarning ? ChatFormatting.RED : ChatFormatting.GREEN;
		MutableComponent styledBody = body.copy().withStyle(textColor);
		Component fullMessage = config.isShowModPrefix()
			? createPrefix().append(styledBody)
			: styledBody;

		MessageDisplayMode mode = config.getMessageDisplayMode();
		if (mode.sendToChat()) {
			player.sendSystemMessage(fullMessage);
		}
		if (mode.sendToActionbar()) {
			player.sendOverlayMessage(fullMessage);
		}

		if (config.isPlayAlertSound()) {
			float pitch = isWarning ? 0.85F : 1.15F;
			player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, pitch);
		}
	}

	public static void sendClientInfo(Component body) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.sendSystemMessage(createPrefix().append(body.copy().withStyle(ChatFormatting.GRAY)));
		}
	}

	public static String formatTicksAsMinutesSeconds(int ticks) {
		int totalSeconds = Math.max(0, (ticks + 19) / 20);
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		if (minutes > 0) {
			return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
		}
		return String.format(Locale.ROOT, "%ds", seconds);
	}
}
