package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.config.BreedingTimerMode;
import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BreedingTimerManager {
	private static final BreedingTimerManager INSTANCE = new BreedingTimerManager();
	private static final String SHARED_KEY = "shared_breeding";

	public static final class BreedingTimerEntry {
		private final String key;
		private String animalName;
		private int remainingTicks;
		private int totalTicks;

		public BreedingTimerEntry(String key, String animalName, int totalTicks) {
			this.key = key;
			this.animalName = animalName;
			this.remainingTicks = totalTicks;
			this.totalTicks = totalTicks;
		}

		public String key() {
			return this.key;
		}

		public String animalName() {
			return this.animalName;
		}

		public int remainingTicks() {
			return this.remainingTicks;
		}

		public int totalTicks() {
			return this.totalTicks;
		}
	}

	private final Map<String, BreedingTimerEntry> activeTimers = new LinkedHashMap<>();
	private long lastFeedGameTime = -100L;
	private int lastFedEntityId = -1;

	private BreedingTimerManager() {
	}

	public static BreedingTimerManager getInstance() {
		return INSTANCE;
	}

	public void onInteractEntity(Player player, Entity entity, InteractionHand hand) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isBreedingTimerEnabled() || player == null || entity == null || player.isSpectator()) {
			return;
		}
		if (!(entity instanceof Animal animal)) {
			return;
		}
		// Only adult animals have the 5-minute breeding cooldown
		if (animal.isBaby() || animal.getAge() != 0) {
			return;
		}

		ItemStack heldStack = player.getItemInHand(hand);
		if (heldStack.isEmpty() || !animal.isFood(heldStack)) {
			return;
		}

		long now = player.level().getGameTime();
		// Avoid duplicate trigger in the same tick on the same entity
		if (now == this.lastFeedGameTime && entity.getId() == this.lastFedEntityId) {
			return;
		}
		this.lastFeedGameTime = now;
		this.lastFedEntityId = entity.getId();

		String animalDisplayName = animal.getType().getDescription().getString();
		startOrResetTimer(animalDisplayName);
	}

	public void startOrResetTimer(String animalDisplayName) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isBreedingTimerEnabled()) {
			return;
		}

		int durationTicks = config.getBreedingCooldownSeconds() * 20;
		String key = config.getBreedingTimerMode() == BreedingTimerMode.PER_ANIMAL_SPECIES
			? animalDisplayName.toLowerCase()
			: SHARED_KEY;

		BreedingTimerEntry existing = this.activeTimers.get(key);
		if (existing != null) {
			// Reset timer to 5 minutes (or configured duration) when an animal is fed
			existing.remainingTicks = durationTicks;
			existing.totalTicks = durationTicks;
			existing.animalName = animalDisplayName;
		} else {
			this.activeTimers.put(key, new BreedingTimerEntry(key, animalDisplayName, durationTicks));
		}
	}

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.isPaused()) {
			return;
		}
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isBreedingTimerEnabled()) {
			this.activeTimers.clear();
			return;
		}

		Iterator<Map.Entry<String, BreedingTimerEntry>> it = this.activeTimers.entrySet().iterator();
		while (it.hasNext()) {
			BreedingTimerEntry entry = it.next().getValue();
			entry.remainingTicks--;
			if (entry.remainingTicks <= 0) {
				it.remove();
				sendBreedingReadyAlert(entry.animalName);
			}
		}
	}

	private void sendBreedingReadyAlert(String animalName) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		String template = config.getBreedingReadyMessage();
		String formatted = template
			.replace("{animal}", animalName)
			.replace("{animals}", animalName);
		ModMessageUtil.sendAlert(Component.literal(formatted), false);
	}

	public Collection<BreedingTimerEntry> getActiveTimers() {
		return Collections.unmodifiableCollection(this.activeTimers.values());
	}

	public void clearAll() {
		this.activeTimers.clear();
	}
}
