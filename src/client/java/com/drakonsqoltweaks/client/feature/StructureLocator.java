package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.StructureReturnMode;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureLocator {
	private static final Set<String> GROUP_ALIASES = Set.of(
		"village",
		"mineshaft",
		"ruined_portal",
		"ocean_ruin",
		"shipwreck"
	);

	private static final Map<String, String> STRUCTURE_ALIASES = Map.ofEntries(
		Map.entry("woodland_mansion", "mansion"),
		Map.entry("woodland_mansions", "mansion"),
		Map.entry("mansions", "mansion"),
		Map.entry("jungle_temple", "jungle_pyramid"),
		Map.entry("jungle_temples", "jungle_pyramid"),
		Map.entry("jungle_pyramids", "jungle_pyramid"),
		Map.entry("desert_temple", "desert_pyramid"),
		Map.entry("desert_temples", "desert_pyramid"),
		Map.entry("desert_pyramids", "desert_pyramid"),
		Map.entry("ocean_monument", "monument"),
		Map.entry("ocean_monuments", "monument"),
		Map.entry("monuments", "monument"),
		Map.entry("guardian_temple", "monument"),
		Map.entry("nether_fortress", "fortress"),
		Map.entry("nether_fortresses", "fortress"),
		Map.entry("fortresses", "fortress"),
		Map.entry("witch_hut", "swamp_hut"),
		Map.entry("witch_huts", "swamp_hut"),
		Map.entry("swamp_witch_hut", "swamp_hut"),
		Map.entry("swamp_huts", "swamp_hut"),
		Map.entry("ruined_portal_standard", "ruined_portal"),
		Map.entry("bastion", "bastion_remnant"),
		Map.entry("bastions", "bastion_remnant"),
		Map.entry("bastion_remnants", "bastion_remnant"),
		Map.entry("outpost", "pillager_outpost"),
		Map.entry("outposts", "pillager_outpost"),
		Map.entry("pillager_outposts", "pillager_outpost"),
		Map.entry("trial_chamber", "trial_chambers"),
		Map.entry("trail_ruin", "trail_ruins"),
		Map.entry("ancient_cities", "ancient_city"),
		Map.entry("end_cities", "end_city"),
		Map.entry("buried_treasures", "buried_treasure"),
		Map.entry("igloos", "igloo"),
		Map.entry("nether_fossils", "nether_fossil"),
		Map.entry("strongholds", "stronghold"),
		Map.entry("villages", "village"),
		Map.entry("mineshafts", "mineshaft"),
		Map.entry("ruined_portals", "ruined_portal"),
		Map.entry("ocean_ruins", "ocean_ruin"),
		Map.entry("shipwrecks", "shipwreck")
	);

	private static final List<String> COMMON_SUGGESTION_ALIASES = List.of(
		"jungle_temple",
		"desert_temple",
		"woodland_mansion",
		"ocean_monument",
		"nether_fortress",
		"witch_hut",
		"bastion",
		"outpost",
		"trial_chamber",
		"trail_ruin"
	);

	private static volatile HolderLookup.Provider cachedVanillaLookup = null;
	private static final Map<Identifier, Set<ResourceKey<Biome>>> BIOME_TAG_CACHE = new ConcurrentHashMap<>();

	private StructureLocator() {
	}

	public record LocatedStructureResult(
		BlockPos pos,
		Identifier structureId,
		String displayName,
		double distanceBlocks,
		Identifier dimensionId
	) {
	}

	public static Collection<String> getStructureSuggestions() {
		Set<String> suggestions = new LinkedHashSet<>(GROUP_ALIASES);
		Minecraft client = Minecraft.getInstance();
		IntegratedServer integratedServer = client.getSingleplayerServer();
		if (integratedServer != null) {
			Optional<? extends Registry<Structure>> serverRegOpt = integratedServer.registryAccess().lookup(Registries.STRUCTURE);
			if (serverRegOpt.isPresent()) {
				addRegistrySuggestions(suggestions, serverRegOpt.get().keySet());
			}
		}
		if (client.level != null) {
			Optional<? extends Registry<Structure>> regOpt = client.level.registryAccess().lookup(Registries.STRUCTURE);
			if (regOpt.isPresent()) {
				addRegistrySuggestions(suggestions, regOpt.get().keySet());
			}
		}
		try {
			HolderLookup.Provider lookup = getOrCreateVanillaLookup();
			Optional<? extends HolderLookup.RegistryLookup<Structure>> vanillaOpt = lookup.lookup(Registries.STRUCTURE);
			if (vanillaOpt.isPresent()) {
				vanillaOpt.get().listElementIds().forEach(key -> {
					Identifier id = key.identifier();
					if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
						suggestions.add(id.getPath());
					}
					suggestions.add(id.toString());
				});
			}
		} catch (Exception ignored) {
			// Fallback common structure names if lookup fails
			suggestions.addAll(List.of(
				"ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid",
				"end_city", "fortress", "igloo", "jungle_pyramid", "mansion",
				"mineshaft", "mineshaft_mesa", "monument", "nether_fossil",
				"ocean_ruin_cold", "ocean_ruin_warm", "pillager_outpost",
				"ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
				"ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean",
				"ruined_portal_swamp", "shipwreck", "shipwreck_beached",
				"stronghold", "swamp_hut", "trail_ruins", "trial_chambers",
				"village_desert", "village_plains", "village_savanna",
				"village_snowy", "village_taiga"
			));
		}
		suggestions.addAll(COMMON_SUGGESTION_ALIASES);
		return suggestions;
	}

	private static void addRegistrySuggestions(Set<String> suggestions, Set<Identifier> ids) {
		for (Identifier id : ids) {
			if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
				suggestions.add(id.getPath());
			}
			suggestions.add(id.toString());
		}
	}

	public static void locateStructureAsync(
		Identifier requestedStructureId,
		int minDistance,
		int maxDistance,
		StructureReturnMode returnMode
	) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}

		if (maxDistance < minDistance) {
			ModMessageUtil.sendAlert(
				Component.literal("Maximum distance (" + maxDistance + ") must be >= minimum distance (" + minDistance + ")."),
				true
			);
			return;
		}

		BlockPos originPos = player.blockPosition();
		ResourceKey<Level> dimensionKey = client.level.dimension();
		Identifier dimensionId = dimensionKey.identifier();
		IntegratedServer integratedServer = client.getSingleplayerServer();

		ModMessageUtil.sendClientInfo(Component.literal(
			"Searching for nearest '" + requestedStructureId.getPath() + "' between "
				+ minDistance + " and " + maxDistance + " blocks..."
		));

		if (integratedServer != null) {
			CompletableFuture.supplyAsync(() -> {
				ServerLevel serverLevel = integratedServer.getLevel(dimensionKey);
				if (serverLevel == null) {
					return null;
				}
				return searchInServerLevel(serverLevel, requestedStructureId, originPos, minDistance, maxDistance, dimensionId);
			}, integratedServer).whenComplete((result, throwable) -> {
				client.execute(() -> handleSearchCompletion(requestedStructureId, minDistance, maxDistance, returnMode, result, throwable, false));
			});
		} else {
			long seed = DrakonsQolConfig.getInstance().getMultiplayerFallbackSeed();
			CompletableFuture.supplyAsync(() -> {
				return searchClientSideWithSeed(dimensionKey, seed, requestedStructureId, originPos, minDistance, maxDistance, dimensionId);
			}, Util.backgroundExecutor()).whenComplete((result, throwable) -> {
				client.execute(() -> handleSearchCompletion(requestedStructureId, minDistance, maxDistance, returnMode, result, throwable, true));
			});
		}
	}

	private static void handleSearchCompletion(
		Identifier requestedId,
		int minDistance,
		int maxDistance,
		StructureReturnMode returnMode,
		LocatedStructureResult result,
		Throwable throwable,
		boolean wasClientFallback
	) {
		if (throwable != null) {
			Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
			ModMessageUtil.sendAlert(
				Component.literal("Failed to search for structure '" + requestedId + "': " + cause.getMessage()),
				true
			);
			return;
		}

		if (result == null) {
			ModMessageUtil.sendAlert(
				Component.literal("No '" + requestedId.getPath() + "' found between " + minDistance + " and " + maxDistance + " blocks."),
				true
			);
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}

		int x = result.pos().getX();
		int y = result.pos().getY();
		int z = result.pos().getZ();
		int distRounded = (int) Math.round(result.distanceBlocks());
		String structLabel = formatStructureDisplayName(result.structureId());
		String dimStr = result.dimensionId().toString();

		if (returnMode == StructureReturnMode.JOURNEYMAP) {
			// JourneyMap clickable chat waypoint syntax: [name:"Name", x:100, y:64, z:-200, dim:minecraft:overworld]
			String waypointText = String.format(
				Locale.ROOT,
				"[name:\"%s\", x:%d, y:%d, z:%d, dim:%s]",
				structLabel,
				x,
				y,
				z,
				dimStr
			);
			String jmCommand = "/jm wpedit " + waypointText;

			MutableComponent waypointComponent = Component.literal(waypointText)
				.withStyle(Style.EMPTY
					.withColor(ChatFormatting.AQUA)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.RunCommand(jmCommand))
					.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to add waypoint to JourneyMap\n")
							.withStyle(ChatFormatting.GREEN)
							.append(Component.literal(waypointText).withStyle(ChatFormatting.GRAY))
					))
				);

			MutableComponent message = ModMessageUtil.createPrefix()
				.append(Component.literal("Found " + structLabel + " (" + distRounded + "m away): ").withStyle(ChatFormatting.GREEN))
				.append(waypointComponent);

			client.player.sendSystemMessage(message);
		} else {
			// Raw coordinates output with click-to-copy convenience
			String rawCoords = x + " " + y + " " + z;
			MutableComponent coordsComponent = Component.literal("X: " + x + ", Y: " + y + ", Z: " + z)
				.withStyle(Style.EMPTY
					.withColor(ChatFormatting.AQUA)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.CopyToClipboard(rawCoords))
					.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to copy coordinates: " + rawCoords).withStyle(ChatFormatting.YELLOW)
					))
				);

			MutableComponent message = ModMessageUtil.createPrefix()
				.append(Component.literal("Found " + structLabel + " (" + distRounded + "m away) at ").withStyle(ChatFormatting.GREEN))
				.append(coordsComponent);

			client.player.sendSystemMessage(message);
		}
	}

	private static LocatedStructureResult searchInServerLevel(
		ServerLevel level,
		Identifier requestedId,
		BlockPos origin,
		int minDistance,
		int maxDistance,
		Identifier dimensionId
	) {
		Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder.Reference<Structure>> matchingHolders = resolveMatchingStructures(structureRegistry, requestedId);
		if (matchingHolders.isEmpty()) {
			throw new IllegalArgumentException("Unknown structure: " + requestedId);
		}

		ChunkGeneratorStructureState generatorState = level.getChunkSource().getGeneratorState();
		StructureManager structureManager = level.structureManager();
		long seed = generatorState.getLevelSeed();

		double minSq = (double) minDistance * (double) minDistance;
		double maxSq = (double) maxDistance * (double) maxDistance;

		LocatedStructureResult bestResult = null;
		double bestDistSq = Double.MAX_VALUE;

		for (Holder.Reference<Structure> holder : matchingHolders) {
			List<StructurePlacement> placements = generatorState.getPlacementsForStructure(holder);
			for (StructurePlacement placement : placements) {
				if (placement instanceof ConcentricRingsStructurePlacement concentric) {
					LocatedStructureResult ringMatch = searchConcentricRingsServer(
						level, generatorState, structureManager, holder, concentric, origin, minSq, maxSq, bestDistSq, dimensionId
					);
					if (ringMatch != null) {
						double dSq = ringMatch.distanceBlocks() * ringMatch.distanceBlocks();
						if (dSq < bestDistSq) {
							bestDistSq = dSq;
							bestResult = ringMatch;
						}
					}
				} else if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
					LocatedStructureResult spreadMatch = searchRandomSpreadServer(
						level, generatorState, structureManager, holder, randomSpread, seed, origin, minDistance, maxDistance, minSq, maxSq, bestDistSq, dimensionId
					);
					if (spreadMatch != null) {
						double dSq = spreadMatch.distanceBlocks() * spreadMatch.distanceBlocks();
						if (dSq < bestDistSq) {
							bestDistSq = dSq;
							bestResult = spreadMatch;
						}
					}
				}
			}
		}

		return bestResult;
	}

	private static LocatedStructureResult searchConcentricRingsServer(
		ServerLevel level,
		ChunkGeneratorStructureState state,
		StructureManager structureManager,
		Holder.Reference<Structure> holder,
		ConcentricRingsStructurePlacement placement,
		BlockPos origin,
		double minSq,
		double maxSq,
		double currentBestSq,
		Identifier dimensionId
	) {
		List<ChunkPos> ringPositions = state.getRingPositionsFor(placement);
		if (ringPositions == null || ringPositions.isEmpty()) {
			return null;
		}

		record Candidate(ChunkPos chunkPos, BlockPos locatePos, double distSq) {}
		List<Candidate> candidates = new ArrayList<>();
		for (ChunkPos chunkPos : ringPositions) {
			BlockPos locatePos = placement.getLocatePos(chunkPos);
			double distSq = horizontalDistSq(origin, locatePos);
			if (distSq >= minSq && distSq <= maxSq && distSq < currentBestSq) {
				candidates.add(new Candidate(chunkPos, locatePos, distSq));
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::distSq));

		for (Candidate c : candidates) {
			if (c.distSq() >= currentBestSq) {
				break;
			}
			BlockPos verifiedPos = verifyServerStructureAtChunk(level, structureManager, holder, placement, c.chunkPos());
			if (verifiedPos != null) {
				int y = estimateStructureY(level, holder.key().identifier(), verifiedPos.getX(), verifiedPos.getZ());
				BlockPos finalPos = new BlockPos(verifiedPos.getX(), y, verifiedPos.getZ());
				return new LocatedStructureResult(
					finalPos,
					holder.key().identifier(),
					formatStructureDisplayName(holder.key().identifier()),
					Math.sqrt(c.distSq()),
					dimensionId
				);
			}
		}
		return null;
	}

	private static LocatedStructureResult searchRandomSpreadServer(
		ServerLevel level,
		ChunkGeneratorStructureState state,
		StructureManager structureManager,
		Holder.Reference<Structure> holder,
		RandomSpreadStructurePlacement placement,
		long seed,
		BlockPos origin,
		int minDistance,
		int maxDistance,
		double minSq,
		double maxSq,
		double currentBestSq,
		Identifier dimensionId
	) {
		int spacing = Math.max(1, placement.spacing());
		int cellBlockSize = spacing * 16;

		int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
		int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

		int minRadiusCells = Math.max(0, (minDistance / cellBlockSize) - 1);
		int maxRadiusCells = Math.min(250, (maxDistance / cellBlockSize) + 2);

		LocatedStructureResult best = null;
		double bestSq = currentBestSq;

		record Candidate(ChunkPos chunkPos, BlockPos locatePos, double distSq) {}

		for (int r = minRadiusCells; r <= maxRadiusCells; r++) {
			// Lower bound on distance for any cell at ring r
			double ringMinPossibleDist = Math.max(0.0, (r - 1.5) * cellBlockSize);
			if (ringMinPossibleDist * ringMinPossibleDist > bestSq) {
				break;
			}

			List<Candidate> ringCandidates = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				boolean edgeX = (dx == -r || dx == r);
				for (int dz = -r; dz <= r; dz++) {
					boolean edgeZ = (dz == -r || dz == r);
					if (!edgeX && !edgeZ) {
						continue;
					}
					int sampleChunkX = originChunkX + spacing * dx;
					int sampleChunkZ = originChunkZ + spacing * dz;
					ChunkPos candidateChunk = placement.getPotentialStructureChunk(seed, sampleChunkX, sampleChunkZ);
					BlockPos locatePos = placement.getLocatePos(candidateChunk);
					double distSq = horizontalDistSq(origin, locatePos);
					if (distSq >= minSq && distSq <= maxSq && distSq < bestSq) {
						ringCandidates.add(new Candidate(candidateChunk, locatePos, distSq));
					}
				}
			}

			if (ringCandidates.isEmpty()) {
				continue;
			}

			ringCandidates.sort(Comparator.comparingDouble(Candidate::distSq));
			for (Candidate c : ringCandidates) {
				if (c.distSq() >= bestSq) {
					break;
				}
				BlockPos verifiedPos = verifyServerStructureAtChunk(level, structureManager, holder, placement, c.chunkPos());
				if (verifiedPos != null) {
					bestSq = c.distSq();
					int y = estimateStructureY(level, holder.key().identifier(), verifiedPos.getX(), verifiedPos.getZ());
					BlockPos finalPos = new BlockPos(verifiedPos.getX(), y, verifiedPos.getZ());
					best = new LocatedStructureResult(
						finalPos,
						holder.key().identifier(),
						formatStructureDisplayName(holder.key().identifier()),
						Math.sqrt(c.distSq()),
						dimensionId
					);
					break;
				}
			}
		}

		return best;
	}

	private static BlockPos verifyServerStructureAtChunk(
		ServerLevel level,
		StructureManager structureManager,
		Holder.Reference<Structure> holder,
		StructurePlacement placement,
		ChunkPos chunkPos
	) {
		Structure structure = holder.value();
		StructureCheckResult checkResult = structureManager.checkStructurePresence(chunkPos, structure, placement, false);
		if (checkResult == StructureCheckResult.START_NOT_PRESENT) {
			return null;
		}
		if (checkResult == StructureCheckResult.START_PRESENT) {
			return placement.getLocatePos(chunkPos);
		}
		ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.STRUCTURE_STARTS);
		StructureStart start = structureManager.getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
		if (start != null && start.isValid()) {
			return placement.getLocatePos(start.getChunkPos());
		}
		return null;
	}

	private static int estimateStructureY(ServerLevel level, Identifier structureId, int blockX, int blockZ) {
		String path = structureId.getPath();
		if ("ancient_city".equals(path) || "trial_chambers".equals(path)) {
			return getDefaultStructureY(structureId);
		}
		int chunkX = SectionPos.blockToSectionCoord(blockX);
		int chunkZ = SectionPos.blockToSectionCoord(blockZ);
		if (level.hasChunk(chunkX, chunkZ)) {
			int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
			if (surfaceY > level.getMinY()) {
				return surfaceY;
			}
		}
		return getDefaultStructureY(structureId);
	}

	private static int getDefaultStructureY(Identifier structureId) {
		return switch (structureId.getPath()) {
			case "ancient_city" -> -51;
			case "trial_chambers" -> -30;
			case "bastion_remnant", "fortress", "nether_fossil" -> 33;
			case "stronghold" -> 0;
			default -> 64;
		};
	}

	private static int[] getStructureSampleHeights(Identifier structureId) {
		return switch (structureId.getPath()) {
			case "ancient_city" -> new int[]{-27, -51, -15};
			case "trial_chambers" -> new int[]{-30, -20, -40};
			case "bastion_remnant", "fortress", "nether_fossil" -> new int[]{33, 48, 64};
			case "mineshaft" -> new int[]{0, 32, 64};
			case "stronghold" -> new int[]{0, -20, 32, 64};
			default -> new int[]{64};
		};
	}

	private static LocatedStructureResult searchClientSideWithSeed(
		ResourceKey<Level> dimensionKey,
		long seed,
		Identifier requestedId,
		BlockPos origin,
		int minDistance,
		int maxDistance,
		Identifier dimensionId
	) {
		HolderLookup.Provider lookup = getOrCreateVanillaLookup();
		HolderLookup.RegistryLookup<Structure> structureLookup = lookup.lookupOrThrow(Registries.STRUCTURE);
		HolderLookup.RegistryLookup<StructureSet> structureSetLookup = lookup.lookupOrThrow(Registries.STRUCTURE_SET);
		HolderLookup.RegistryLookup<Biome> biomeLookup = lookup.lookupOrThrow(Registries.BIOME);

		List<Holder.Reference<Structure>> matchingHolders = resolveMatchingStructures(structureLookup, requestedId);
		if (matchingHolders.isEmpty()) {
			throw new IllegalArgumentException("Unknown structure: " + requestedId);
		}

		ResourceKey<NoiseGeneratorSettings> noiseKey = NoiseGeneratorSettings.OVERWORLD;
		BiomeSource biomeSource;
		if (dimensionKey.equals(Level.NETHER)) {
			noiseKey = NoiseGeneratorSettings.NETHER;
			HolderLookup.RegistryLookup<MultiNoiseBiomeSourceParameterList> presetLookup =
				lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
			Holder.Reference<MultiNoiseBiomeSourceParameterList> netherPreset =
				presetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
			biomeSource = MultiNoiseBiomeSource.createFromPreset(netherPreset);
		} else if (dimensionKey.equals(Level.END)) {
			noiseKey = NoiseGeneratorSettings.END;
			biomeSource = TheEndBiomeSource.create(biomeLookup);
		} else {
			HolderLookup.RegistryLookup<MultiNoiseBiomeSourceParameterList> presetLookup =
				lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
			Holder.Reference<MultiNoiseBiomeSourceParameterList> overworldPreset =
				presetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
			biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldPreset);
		}

		RandomState randomState = RandomState.create(lookup, noiseKey, seed);
		Set<ResourceKey<Biome>> dimensionBiomes = collectDimensionBiomeKeys(biomeSource);

		double minSq = (double) minDistance * (double) minDistance;
		double maxSq = (double) maxDistance * (double) maxDistance;
		LocatedStructureResult best = null;
		double bestSq = Double.MAX_VALUE;

		int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
		int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

		record Candidate(ChunkPos chunkPos, BlockPos locatePos, double distSq) {}

		for (Holder.Reference<Structure> holder : matchingHolders) {
			Set<ResourceKey<Biome>> validStructureBiomes = resolveHolderSetBiomeKeys(holder.value().biomes());
			if (!intersects(validStructureBiomes, dimensionBiomes)) {
				continue;
			}

			List<StructurePlacement> placements = findPlacementsForStructure(holder, structureSetLookup, dimensionBiomes);
			int defaultY = getDefaultStructureY(holder.key().identifier());

			for (StructurePlacement placement : placements) {
				if (placement instanceof ConcentricRingsStructurePlacement concentric) {
					List<ChunkPos> rings = computeConcentricRingPositions(concentric, biomeSource, randomState, seed);
					for (ChunkPos cp : rings) {
						BlockPos locatePos = concentric.getLocatePos(cp);
						double dSq = horizontalDistSq(origin, locatePos);
						if (dSq >= minSq && dSq <= maxSq && dSq < bestSq) {
							bestSq = dSq;
							best = new LocatedStructureResult(
								new BlockPos(locatePos.getX(), defaultY, locatePos.getZ()),
								holder.key().identifier(),
								formatStructureDisplayName(holder.key().identifier()),
								Math.sqrt(dSq),
								dimensionId
							);
						}
					}
				} else if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
					int spacing = Math.max(1, randomSpread.spacing());
					int cellBlockSize = spacing * 16;
					int minR = Math.max(0, (minDistance / cellBlockSize) - 1);
					int maxR = Math.min(250, (maxDistance / cellBlockSize) + 2);

					for (int r = minR; r <= maxR; r++) {
						double ringMinPossibleDist = Math.max(0.0, (r - 1.5) * cellBlockSize);
						if (ringMinPossibleDist * ringMinPossibleDist > bestSq) {
							break;
						}

						List<Candidate> ringCandidates = new ArrayList<>();
						for (int dx = -r; dx <= r; dx++) {
							boolean edgeX = (dx == -r || dx == r);
							for (int dz = -r; dz <= r; dz++) {
								boolean edgeZ = (dz == -r || dz == r);
								if (!edgeX && !edgeZ) {
									continue;
								}
								int sampleX = originChunkX + spacing * dx;
								int sampleZ = originChunkZ + spacing * dz;
								ChunkPos cp = randomSpread.getPotentialStructureChunk(seed, sampleX, sampleZ);
								BlockPos locatePos = randomSpread.getLocatePos(cp);
								double dSq = horizontalDistSq(origin, locatePos);
								if (dSq >= minSq && dSq <= maxSq && dSq < bestSq) {
									ringCandidates.add(new Candidate(cp, locatePos, dSq));
								}
							}
						}

						if (ringCandidates.isEmpty()) {
							continue;
						}

						ringCandidates.sort(Comparator.comparingDouble(Candidate::distSq));
						for (Candidate c : ringCandidates) {
							if (c.distSq() >= bestSq) {
								break;
							}
							if (!isClientPlacementValid(holder, randomSpread, c.chunkPos().x(), c.chunkPos().z(), seed, structureSetLookup, dimensionBiomes)) {
								continue;
							}
							if (isClientStructureBiomeValid(holder, validStructureBiomes, c.chunkPos(), biomeSource, randomState)) {
								bestSq = c.distSq();
								best = new LocatedStructureResult(
									new BlockPos(c.locatePos().getX(), defaultY, c.locatePos().getZ()),
									holder.key().identifier(),
									formatStructureDisplayName(holder.key().identifier()),
									Math.sqrt(c.distSq()),
									dimensionId
								);
								break;
							}
						}
					}
				}
			}
		}

		return best;
	}

	private static Set<ResourceKey<Biome>> collectDimensionBiomeKeys(BiomeSource biomeSource) {
		Set<ResourceKey<Biome>> keys = new HashSet<>();
		for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
			holder.unwrapKey().ifPresent(keys::add);
		}
		return keys;
	}

	private static boolean intersects(Set<ResourceKey<Biome>> a, Set<ResourceKey<Biome>> b) {
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		for (ResourceKey<Biome> key : a) {
			if (b.contains(key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBiomesForStructureSet(StructureSet structureSet, Set<ResourceKey<Biome>> dimensionBiomes) {
		for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
			Set<ResourceKey<Biome>> biomes = resolveHolderSetBiomeKeys(entry.structure().value().biomes());
			if (intersects(biomes, dimensionBiomes)) {
				return true;
			}
		}
		return false;
	}

	private static List<StructurePlacement> findPlacementsForStructure(
		Holder.Reference<Structure> targetStructure,
		HolderLookup.RegistryLookup<StructureSet> structureSetLookup,
		Set<ResourceKey<Biome>> dimensionBiomes
	) {
		List<StructurePlacement> placements = new ArrayList<>();
		ResourceKey<Structure> targetKey = targetStructure.key();
		structureSetLookup.listElements().forEach(setHolder -> {
			StructureSet set = setHolder.value();
			boolean containsTarget = false;
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (entry.structure().unwrapKey().map(targetKey::equals).orElse(false)) {
					containsTarget = true;
					break;
				}
			}
			if (containsTarget && hasBiomesForStructureSet(set, dimensionBiomes)) {
				placements.add(set.placement());
			}
		});
		return placements;
	}

	private static boolean isClientPlacementValid(
		Holder.Reference<Structure> holder,
		RandomSpreadStructurePlacement placement,
		int chunkX,
		int chunkZ,
		long seed,
		HolderLookup.RegistryLookup<StructureSet> structureSetLookup,
		Set<ResourceKey<Biome>> dimensionBiomes
	) {
		if (!placement.applyAdditionalChunkRestrictions(chunkX, chunkZ, seed)) {
			return false;
		}
		if ("pillager_outpost".equals(holder.key().identifier().getPath())) {
			Optional<Holder.Reference<StructureSet>> villagesOpt = structureSetLookup.get(BuiltinStructureSets.VILLAGES);
			if (villagesOpt.isPresent()
				&& hasBiomesForStructureSet(villagesOpt.get().value(), dimensionBiomes)
				&& villagesOpt.get().value().placement() instanceof RandomSpreadStructurePlacement villageSpread) {
				int radius = 10;
				for (int i = chunkX - radius; i <= chunkX + radius; i++) {
					for (int j = chunkZ - radius; j <= chunkZ + radius; j++) {
						ChunkPos potential = villageSpread.getPotentialStructureChunk(seed, i, j);
						if (potential.x() == i && potential.z() == j
							&& villageSpread.applyAdditionalChunkRestrictions(i, j, seed)) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	private static boolean isClientStructureBiomeValid(
		Holder.Reference<Structure> holder,
		Set<ResourceKey<Biome>> validBiomes,
		ChunkPos chunkPos,
		BiomeSource biomeSource,
		RandomState randomState
	) {
		Identifier structureId = holder.key().identifier();
		if ("monument".equals(structureId.getPath())) {
			Set<ResourceKey<Biome>> surroundingBiomes = resolveBiomeTagKeys(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING);
			Set<Holder<Biome>> radiusBiomes = biomeSource.getBiomesWithin(
				chunkPos.getMiddleBlockX(),
				63,
				chunkPos.getMiddleBlockZ(),
				29,
				randomState.sampler()
			);
			for (Holder<Biome> b : radiusBiomes) {
				Optional<ResourceKey<Biome>> keyOpt = b.unwrapKey();
				if (keyOpt.isEmpty() || !surroundingBiomes.contains(keyOpt.get())) {
					return false;
				}
			}
		}

		int sampleBlockX = chunkPos.getMiddleBlockX();
		int sampleBlockZ = chunkPos.getMiddleBlockZ();
		int quartX = QuartPos.fromBlock(sampleBlockX);
		int quartZ = QuartPos.fromBlock(sampleBlockZ);

		for (int sampleY : getStructureSampleHeights(structureId)) {
			Holder<Biome> chunkBiome = biomeSource.getNoiseBiome(
				quartX,
				QuartPos.fromBlock(sampleY),
				quartZ,
				randomState.sampler()
			);
			Optional<ResourceKey<Biome>> biomeKeyOpt = chunkBiome.unwrapKey();
			if (biomeKeyOpt.isPresent() && validBiomes.contains(biomeKeyOpt.get())) {
				return true;
			}
		}
		return false;
	}

	private static List<ChunkPos> computeConcentricRingPositions(
		ConcentricRingsStructurePlacement placement,
		BiomeSource biomeSource,
		RandomState randomState,
		long seed
	) {
		int count = placement.count();
		if (count <= 0) {
			return List.of();
		}
		int distance = placement.distance();
		int spread = Math.max(1, placement.spread());
		Set<ResourceKey<Biome>> preferredBiomes = resolveHolderSetBiomeKeys(placement.preferredBiomes());

		RandomSource random = RandomSource.create();
		random.setSeed(seed);
		double angle = random.nextDouble() * Math.PI * 2.0;
		int positionInRing = 0;
		int ringIndex = 0;

		List<ChunkPos> result = new ArrayList<>(count);
		for (int idx = 0; idx < count; idx++) {
			double dist = (double) (4 * distance + distance * ringIndex * 6)
				+ (random.nextDouble() - 0.5) * (double) distance * 2.5;
			int chunkX = (int) Math.round(Math.cos(angle) * dist);
			int chunkZ = (int) Math.round(Math.sin(angle) * dist);
			RandomSource forkedRandom = random.fork();

			Pair<BlockPos, Holder<Biome>> pair = biomeSource.findBiomeHorizontal(
				SectionPos.sectionToBlockCoord(chunkX, 8),
				0,
				SectionPos.sectionToBlockCoord(chunkZ, 8),
				112,
				b -> b.unwrapKey().map(preferredBiomes::contains).orElse(false),
				forkedRandom,
				randomState.sampler()
			);
			if (pair != null) {
				BlockPos pos = pair.getFirst();
				result.add(new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
			} else {
				result.add(new ChunkPos(chunkX, chunkZ));
			}

			angle += (Math.PI * 2.0) / (double) spread;
			if (++positionInRing == spread) {
				ringIndex++;
				positionInRing = 0;
				spread += 2 * spread / (ringIndex + 1);
				spread = Math.min(spread, count - idx);
				angle += random.nextDouble() * Math.PI * 2.0;
			}
		}
		return result;
	}

	private static Set<ResourceKey<Biome>> resolveHolderSetBiomeKeys(HolderSet<Biome> holderSet) {
		Either<TagKey<Biome>, List<Holder<Biome>>> unwrapped = holderSet.unwrap();
		Optional<TagKey<Biome>> tagOpt = unwrapped.left();
		if (tagOpt.isPresent()) {
			return resolveBiomeTagKeys(tagOpt.get());
		}
		Optional<List<Holder<Biome>>> listOpt = unwrapped.right();
		if (listOpt.isPresent()) {
			Set<ResourceKey<Biome>> keys = new HashSet<>();
			for (Holder<Biome> holder : listOpt.get()) {
				holder.unwrapKey().ifPresent(keys::add);
			}
			return keys;
		}
		return Set.of();
	}

	private static Set<ResourceKey<Biome>> resolveBiomeTagKeys(TagKey<Biome> tagKey) {
		Identifier tagId = tagKey.location();
		Set<ResourceKey<Biome>> cached = BIOME_TAG_CACHE.get(tagId);
		if (cached != null) {
			return cached;
		}

		// 1) Try client level registry if tags are bound in an active world
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.level != null) {
			Optional<? extends Registry<Biome>> biomeRegOpt = client.level.registryAccess().lookup(Registries.BIOME);
			if (biomeRegOpt.isPresent()) {
				Optional<HolderSet.Named<Biome>> namedOpt = biomeRegOpt.get().get(tagKey);
				if (namedOpt.isPresent() && namedOpt.get().isBound()) {
					Set<ResourceKey<Biome>> keys = new HashSet<>();
					for (Holder<Biome> h : namedOpt.get()) {
						h.unwrapKey().ifPresent(keys::add);
					}
					if (!keys.isEmpty()) {
						Set<ResourceKey<Biome>> unmodifiable = Collections.unmodifiableSet(keys);
						BIOME_TAG_CACHE.put(tagId, unmodifiable);
						return unmodifiable;
					}
				}
			}
		}

		// 2) Fallback: parse data/<namespace>/tags/worldgen/biome/<path>.json from classpath resources
		Set<ResourceKey<Biome>> resolved = Collections.unmodifiableSet(
			resolveBiomeTagFromResources(tagId, new HashSet<>())
		);
		if (!resolved.isEmpty()) {
			BIOME_TAG_CACHE.put(tagId, resolved);
		}
		return resolved;
	}

	private static Set<ResourceKey<Biome>> resolveBiomeTagFromResources(Identifier tagId, Set<Identifier> visited) {
		if (!visited.add(tagId)) {
			return Set.of();
		}
		String resourcePath = "/data/" + tagId.getNamespace() + "/tags/worldgen/biome/" + tagId.getPath() + ".json";
		Set<ResourceKey<Biome>> keys = new HashSet<>();
		try (InputStream stream = StructureLocator.class.getResourceAsStream(resourcePath)) {
			if (stream == null) {
				return Set.of();
			}
			JsonElement rootElement = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			if (!rootElement.isJsonObject()) {
				return Set.of();
			}
			JsonObject root = rootElement.getAsJsonObject();
			JsonArray values = root.getAsJsonArray("values");
			if (values == null) {
				return Set.of();
			}
			for (JsonElement element : values) {
				String entryStr = null;
				if (element.isJsonPrimitive()) {
					entryStr = element.getAsString();
				} else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
					entryStr = element.getAsJsonObject().get("id").getAsString();
				}
				if (entryStr == null || entryStr.isEmpty()) {
					continue;
				}
				if (entryStr.startsWith("#")) {
					Identifier nestedTagId = Identifier.tryParse(entryStr.substring(1));
					if (nestedTagId != null) {
						keys.addAll(resolveBiomeTagFromResources(nestedTagId, visited));
					}
				} else {
					Identifier biomeId = Identifier.tryParse(entryStr);
					if (biomeId != null) {
						keys.add(ResourceKey.create(Registries.BIOME, biomeId));
					}
				}
			}
		} catch (Exception ignored) {
		}
		return keys;
	}

	private static HolderLookup.Provider getOrCreateVanillaLookup() {
		HolderLookup.Provider local = cachedVanillaLookup;
		if (local == null) {
			synchronized (StructureLocator.class) {
				local = cachedVanillaLookup;
				if (local == null) {
					local = VanillaRegistries.createLookup();
					cachedVanillaLookup = local;
				}
			}
		}
		return local;
	}

	private static List<Holder.Reference<Structure>> resolveMatchingStructures(
		HolderLookup<Structure> lookup,
		Identifier requestedId
	) {
		List<Holder.Reference<Structure>> results = new ArrayList<>();
		String rawPath = requestedId.getPath().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		String canonicalPath = STRUCTURE_ALIASES.getOrDefault(rawPath, rawPath);
		Identifier canonicalId = Identifier.fromNamespaceAndPath(requestedId.getNamespace(), canonicalPath);

		// Group aliases (e.g., village, mineshaft, ruined_portal, ocean_ruin, shipwreck) match both the base ID and all variants
		if (GROUP_ALIASES.contains(canonicalPath)) {
			lookup.listElements().forEach(holder -> {
				Identifier id = holder.key().identifier();
				String path = id.getPath().toLowerCase(Locale.ROOT);
				if (path.equals(canonicalPath) || path.startsWith(canonicalPath + "_")) {
					results.add(holder);
				}
			});
			if (!results.isEmpty()) {
				return results;
			}
		}

		ResourceKey<Structure> exactKey = ResourceKey.create(Registries.STRUCTURE, canonicalId);
		Optional<Holder.Reference<Structure>> exact = lookup.get(exactKey);
		if (exact.isPresent()) {
			results.add(exact.get());
			return results;
		}

		lookup.listElements().forEach(holder -> {
			Identifier id = holder.key().identifier();
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (path.equals(canonicalPath) || path.startsWith(canonicalPath + "_")) {
				results.add(holder);
			}
		});
		return results;
	}

	private static double horizontalDistSq(BlockPos a, BlockPos b) {
		double dx = (double) a.getX() - (double) b.getX();
		double dz = (double) a.getZ() - (double) b.getZ();
		return dx * dx + dz * dz;
	}

	public static String formatStructureDisplayName(Identifier id) {
		String path = id.getPath();
		String[] parts = path.split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1));
			}
		}
		return sb.isEmpty() ? path : sb.toString();
	}
}
