package com.drakonsqoltweaks.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DrakonsQolConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("DrakonsQolTweaks");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static DrakonsQolConfig instance;

	// --- 1. Hotbar / Block Refill Options ---
	private boolean hotbarRefillEnabled = true;
	private RefillFilterMode refillFilterMode = RefillFilterMode.ALL_ITEMS;
	private boolean refillOffhand = true;
	private boolean refillMatchComponents = true;
	private boolean refillOnDrop = false;
	private boolean notifyOnRefill = false;

	// --- 2. Timers Options ---
	// Lit TNT Timer
	private boolean tntTimerEnabled = true;
	private TntTimerFormat tntTimerFormat = TntTimerFormat.SECONDS_AND_MILLISECONDS;
	private boolean tntDynamicColor = true;

	// Animal Breeding Timer
	private boolean breedingTimerEnabled = true;
	private int breedingCooldownSeconds = 300; // 5 minutes (300 seconds)
	private BreedingTimerMode breedingTimerMode = BreedingTimerMode.SHARED_RESET_ON_FEED;

	// Furnace Smelting Timer
	private boolean furnaceTimerEnabled = true;
	private boolean showTimerInFurnaceGui = true;
	private boolean showActiveTimersOnHud = true;

	// --- 3. Mod Messages & Notifications Options ---
	private boolean modMessagesEnabled = true;
	private MessageDisplayMode messageDisplayMode = MessageDisplayMode.CHAT_ONLY;
	private boolean showModPrefix = true;
	private boolean playAlertSound = true;
	private String breedingReadyMessage = "Animals can now be bred again! ({animal})";
	private String furnaceDoneMessage = "{furnace} finished smelting {count}x {item}!";
	private String furnaceFuelOutMessage = "{furnace} ran out of fuel while smelting {item}!";

	// --- 4. Structure Finder (/dqol s) Options ---
	private int defaultStructureMaxDistance = 10000;
	private StructureReturnMode defaultStructureReturnMode = StructureReturnMode.JOURNEYMAP;
	private long multiplayerFallbackSeed = 0L;

	// --- 5. Extensible Future Features Map ---
	private Map<String, Boolean> extraFeatureToggles = new HashMap<>();

	public static DrakonsQolConfig getInstance() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("drakons-qol-tweaks.json");
	}

	public static DrakonsQolConfig load() {
		Path path = getConfigPath();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				DrakonsQolConfig loaded = GSON.fromJson(reader, DrakonsQolConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					instance = loaded;
					return loaded;
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to read Drakon's QOL Tweaks config from {}, using defaults", path, e);
			}
		}
		DrakonsQolConfig defaults = new DrakonsQolConfig();
		defaults.sanitize();
		instance = defaults;
		defaults.save();
		return defaults;
	}

	public void resetToDefaults() {
		DrakonsQolConfig defaults = new DrakonsQolConfig();
		this.hotbarRefillEnabled = defaults.hotbarRefillEnabled;
		this.refillFilterMode = defaults.refillFilterMode;
		this.refillOffhand = defaults.refillOffhand;
		this.refillMatchComponents = defaults.refillMatchComponents;
		this.refillOnDrop = defaults.refillOnDrop;
		this.notifyOnRefill = defaults.notifyOnRefill;

		this.tntTimerEnabled = defaults.tntTimerEnabled;
		this.tntTimerFormat = defaults.tntTimerFormat;
		this.tntDynamicColor = defaults.tntDynamicColor;

		this.breedingTimerEnabled = defaults.breedingTimerEnabled;
		this.breedingCooldownSeconds = defaults.breedingCooldownSeconds;
		this.breedingTimerMode = defaults.breedingTimerMode;

		this.furnaceTimerEnabled = defaults.furnaceTimerEnabled;
		this.showTimerInFurnaceGui = defaults.showTimerInFurnaceGui;
		this.showActiveTimersOnHud = defaults.showActiveTimersOnHud;

		this.modMessagesEnabled = defaults.modMessagesEnabled;
		this.messageDisplayMode = defaults.messageDisplayMode;
		this.showModPrefix = defaults.showModPrefix;
		this.playAlertSound = defaults.playAlertSound;
		this.breedingReadyMessage = defaults.breedingReadyMessage;
		this.furnaceDoneMessage = defaults.furnaceDoneMessage;
		this.furnaceFuelOutMessage = defaults.furnaceFuelOutMessage;

		this.defaultStructureMaxDistance = defaults.defaultStructureMaxDistance;
		this.defaultStructureReturnMode = defaults.defaultStructureReturnMode;
		this.multiplayerFallbackSeed = defaults.multiplayerFallbackSeed;
		this.extraFeatureToggles.clear();
		this.save();
	}

	private void sanitize() {
		if (this.refillFilterMode == null) this.refillFilterMode = RefillFilterMode.ALL_ITEMS;
		if (this.tntTimerFormat == null) this.tntTimerFormat = TntTimerFormat.SECONDS_AND_MILLISECONDS;
		if (this.breedingTimerMode == null) this.breedingTimerMode = BreedingTimerMode.SHARED_RESET_ON_FEED;
		if (this.messageDisplayMode == null) this.messageDisplayMode = MessageDisplayMode.CHAT_ONLY;
		if (this.defaultStructureReturnMode == null) this.defaultStructureReturnMode = StructureReturnMode.JOURNEYMAP;
		if (this.breedingCooldownSeconds < 5) this.breedingCooldownSeconds = 300;
		if (this.breedingCooldownSeconds > 3600) this.breedingCooldownSeconds = 3600;
		if (this.defaultStructureMaxDistance < 64) this.defaultStructureMaxDistance = 10000;
		if (this.defaultStructureMaxDistance > 100000) this.defaultStructureMaxDistance = 100000;
		if (this.breedingReadyMessage == null || this.breedingReadyMessage.isBlank()) {
			this.breedingReadyMessage = "Animals can now be bred again! ({animal})";
		}
		if (this.furnaceDoneMessage == null || this.furnaceDoneMessage.isBlank()) {
			this.furnaceDoneMessage = "{furnace} finished smelting {count}x {item}!";
		}
		if (this.furnaceFuelOutMessage == null || this.furnaceFuelOutMessage.isBlank()) {
			this.furnaceFuelOutMessage = "{furnace} ran out of fuel while smelting {item}!";
		}
		if (this.extraFeatureToggles == null) {
			this.extraFeatureToggles = new HashMap<>();
		}
	}

	public void save() {
		Path path = getConfigPath();
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to save Drakon's QOL Tweaks config to {}", path, e);
		}
	}

	// Getters and Setters
	public boolean isHotbarRefillEnabled() {
		return this.hotbarRefillEnabled;
	}

	public void setHotbarRefillEnabled(boolean hotbarRefillEnabled) {
		this.hotbarRefillEnabled = hotbarRefillEnabled;
	}

	public RefillFilterMode getRefillFilterMode() {
		return this.refillFilterMode;
	}

	public void setRefillFilterMode(RefillFilterMode refillFilterMode) {
		this.refillFilterMode = refillFilterMode != null ? refillFilterMode : RefillFilterMode.ALL_ITEMS;
	}

	public boolean isRefillOffhand() {
		return this.refillOffhand;
	}

	public void setRefillOffhand(boolean refillOffhand) {
		this.refillOffhand = refillOffhand;
	}

	public boolean isRefillMatchComponents() {
		return this.refillMatchComponents;
	}

	public void setRefillMatchComponents(boolean refillMatchComponents) {
		this.refillMatchComponents = refillMatchComponents;
	}

	public boolean isRefillOnDrop() {
		return this.refillOnDrop;
	}

	public void setRefillOnDrop(boolean refillOnDrop) {
		this.refillOnDrop = refillOnDrop;
	}

	public boolean isNotifyOnRefill() {
		return this.notifyOnRefill;
	}

	public void setNotifyOnRefill(boolean notifyOnRefill) {
		this.notifyOnRefill = notifyOnRefill;
	}

	public boolean isTntTimerEnabled() {
		return this.tntTimerEnabled;
	}

	public void setTntTimerEnabled(boolean tntTimerEnabled) {
		this.tntTimerEnabled = tntTimerEnabled;
	}

	public TntTimerFormat getTntTimerFormat() {
		return this.tntTimerFormat;
	}

	public void setTntTimerFormat(TntTimerFormat tntTimerFormat) {
		this.tntTimerFormat = tntTimerFormat != null ? tntTimerFormat : TntTimerFormat.SECONDS_AND_MILLISECONDS;
	}

	public boolean isTntDynamicColor() {
		return this.tntDynamicColor;
	}

	public void setTntDynamicColor(boolean tntDynamicColor) {
		this.tntDynamicColor = tntDynamicColor;
	}

	public boolean isBreedingTimerEnabled() {
		return this.breedingTimerEnabled;
	}

	public void setBreedingTimerEnabled(boolean breedingTimerEnabled) {
		this.breedingTimerEnabled = breedingTimerEnabled;
	}

	public int getBreedingCooldownSeconds() {
		return this.breedingCooldownSeconds;
	}

	public void setBreedingCooldownSeconds(int breedingCooldownSeconds) {
		this.breedingCooldownSeconds = Math.clamp(breedingCooldownSeconds, 5, 3600);
	}

	public BreedingTimerMode getBreedingTimerMode() {
		return this.breedingTimerMode;
	}

	public void setBreedingTimerMode(BreedingTimerMode breedingTimerMode) {
		this.breedingTimerMode = breedingTimerMode != null ? breedingTimerMode : BreedingTimerMode.SHARED_RESET_ON_FEED;
	}

	public boolean isFurnaceTimerEnabled() {
		return this.furnaceTimerEnabled;
	}

	public void setFurnaceTimerEnabled(boolean furnaceTimerEnabled) {
		this.furnaceTimerEnabled = furnaceTimerEnabled;
	}

	public boolean isShowTimerInFurnaceGui() {
		return this.showTimerInFurnaceGui;
	}

	public void setShowTimerInFurnaceGui(boolean showTimerInFurnaceGui) {
		this.showTimerInFurnaceGui = showTimerInFurnaceGui;
	}

	public boolean isShowActiveTimersOnHud() {
		return this.showActiveTimersOnHud;
	}

	public void setShowActiveTimersOnHud(boolean showActiveTimersOnHud) {
		this.showActiveTimersOnHud = showActiveTimersOnHud;
	}

	public boolean isModMessagesEnabled() {
		return this.modMessagesEnabled;
	}

	public void setModMessagesEnabled(boolean modMessagesEnabled) {
		this.modMessagesEnabled = modMessagesEnabled;
	}

	public MessageDisplayMode getMessageDisplayMode() {
		return this.messageDisplayMode;
	}

	public void setMessageDisplayMode(MessageDisplayMode messageDisplayMode) {
		this.messageDisplayMode = messageDisplayMode != null ? messageDisplayMode : MessageDisplayMode.CHAT_ONLY;
	}

	public boolean isShowModPrefix() {
		return this.showModPrefix;
	}

	public void setShowModPrefix(boolean showModPrefix) {
		this.showModPrefix = showModPrefix;
	}

	public boolean isPlayAlertSound() {
		return this.playAlertSound;
	}

	public void setPlayAlertSound(boolean playAlertSound) {
		this.playAlertSound = playAlertSound;
	}

	public String getBreedingReadyMessage() {
		return this.breedingReadyMessage;
	}

	public void setBreedingReadyMessage(String breedingReadyMessage) {
		this.breedingReadyMessage = breedingReadyMessage != null ? breedingReadyMessage : "";
	}

	public String getFurnaceDoneMessage() {
		return this.furnaceDoneMessage;
	}

	public void setFurnaceDoneMessage(String furnaceDoneMessage) {
		this.furnaceDoneMessage = furnaceDoneMessage != null ? furnaceDoneMessage : "";
	}

	public String getFurnaceFuelOutMessage() {
		return this.furnaceFuelOutMessage;
	}

	public void setFurnaceFuelOutMessage(String furnaceFuelOutMessage) {
		this.furnaceFuelOutMessage = furnaceFuelOutMessage != null ? furnaceFuelOutMessage : "";
	}

	public int getDefaultStructureMaxDistance() {
		return this.defaultStructureMaxDistance;
	}

	public void setDefaultStructureMaxDistance(int defaultStructureMaxDistance) {
		this.defaultStructureMaxDistance = Math.clamp(defaultStructureMaxDistance, 64, 100000);
	}

	public StructureReturnMode getDefaultStructureReturnMode() {
		return this.defaultStructureReturnMode != null ? this.defaultStructureReturnMode : StructureReturnMode.JOURNEYMAP;
	}

	public void setDefaultStructureReturnMode(StructureReturnMode defaultStructureReturnMode) {
		this.defaultStructureReturnMode = defaultStructureReturnMode != null
			? defaultStructureReturnMode
			: StructureReturnMode.JOURNEYMAP;
	}

	public long getMultiplayerFallbackSeed() {
		return this.multiplayerFallbackSeed;
	}

	public void setMultiplayerFallbackSeed(long multiplayerFallbackSeed) {
		this.multiplayerFallbackSeed = multiplayerFallbackSeed;
	}

	public boolean getExtraFeatureEnabled(String featureId, boolean defaultValue) {
		return this.extraFeatureToggles.getOrDefault(featureId, defaultValue);
	}

	public void setExtraFeatureEnabled(String featureId, boolean enabled) {
		this.extraFeatureToggles.put(featureId, enabled);
	}
}
