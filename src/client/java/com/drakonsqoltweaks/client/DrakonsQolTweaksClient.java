package com.drakonsqoltweaks.client;

import com.drakonsqoltweaks.client.command.DrakonsQolCommands;
import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.QolFeatureRegistry;
import com.drakonsqoltweaks.client.feature.BreedingTimerManager;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.feature.HotbarRefillManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;

public class DrakonsQolTweaksClient implements ClientModInitializer {
	private static volatile Screen pendingScreen = null;

	@Override
	public void onInitializeClient() {
		// Load persistent configuration
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();

		// Register quick-access toggles in the extensible feature registry
		QolFeatureRegistry.registerCustomFeature(
			"hotbar_refill",
			"Hotbar Auto-Refill",
			"Master toggle for automatic hotbar stack refilling",
			config::isHotbarRefillEnabled,
			val -> {
				config.setHotbarRefillEnabled(val);
				config.save();
			}
		);
		QolFeatureRegistry.registerCustomFeature(
			"tnt_timer",
			"Lit TNT Timer",
			"Master toggle for floating countdown timers above lit TNT",
			config::isTntTimerEnabled,
			val -> {
				config.setTntTimerEnabled(val);
				config.save();
			}
		);
		QolFeatureRegistry.registerCustomFeature(
			"breeding_timer",
			"Animal Breeding Timer",
			"Master toggle for 5-minute animal breeding cooldown timers",
			config::isBreedingTimerEnabled,
			val -> {
				config.setBreedingTimerEnabled(val);
				config.save();
			}
		);
		QolFeatureRegistry.registerCustomFeature(
			"furnace_timer",
			"Furnace Smelting Timer",
			"Master toggle for furnace, blast furnace, and smoker timers",
			config::isFurnaceTimerEnabled,
			val -> {
				config.setFurnaceTimerEnabled(val);
				config.save();
			}
		);

		// Register client-side commands (/drakonsqol and /dqol)
		DrakonsQolCommands.register();

		// Tick client-side managers every tick
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen toOpen = pendingScreen;
			if (toOpen != null) {
				pendingScreen = null;
				client.gui.setScreen(toOpen);
			}

			HotbarRefillManager.getInstance().tick(client);
			BreedingTimerManager.getInstance().tick(client);
			FurnaceTimerManager.getInstance().tick(client);
		});
	}

	public static void scheduleOpenScreen(Screen screen) {
		pendingScreen = screen;
	}
}
