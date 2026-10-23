package com.drakonsqoltweaks.client.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Extensible registry for future QOL features to register toggleable options
 * that automatically appear in the Drakon's QOL Tweaks Options Screen.
 */
public final class QolFeatureRegistry {
	private static final List<FeatureEntry> ENTRIES = new ArrayList<>();

	private QolFeatureRegistry() {
	}

	public record FeatureEntry(
		String id,
		String displayName,
		String description,
		BooleanSupplier getter,
		Consumer<Boolean> setter
	) {
	}

	public static void registerToggleFeature(String id, String displayName, String description, boolean defaultValue) {
		ENTRIES.removeIf(e -> e.id().equals(id));
		ENTRIES.add(new FeatureEntry(
			id,
			displayName,
			description,
			() -> DrakonsQolConfig.getInstance().getExtraFeatureEnabled(id, defaultValue),
			val -> {
				DrakonsQolConfig.getInstance().setExtraFeatureEnabled(id, val);
				DrakonsQolConfig.getInstance().save();
			}
		));
	}

	public static void registerCustomFeature(String id, String displayName, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
		ENTRIES.removeIf(e -> e.id().equals(id));
		ENTRIES.add(new FeatureEntry(id, displayName, description, getter, setter));
	}

	public static List<FeatureEntry> getRegisteredFeatures() {
		return Collections.unmodifiableList(ENTRIES);
	}
}
