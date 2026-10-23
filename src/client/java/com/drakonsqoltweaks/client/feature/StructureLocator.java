package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.StructureReturnMode;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class StructureLocator {
	private static final List<String> GROUP_ALIASES = List.of(
		"village",
		"mineshaft",
		"ruined_portal",
		"ocean_ruin",
		"shipwreck"
	);

	private static volatile HolderLookup.Provider cachedVanillaLookup = null;

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
		if (client.level != null) {
			Optional<? extends Registry<Structure>> regOpt = client.level.registryAccess().lookup(Registries.STRUCTURE);
			if (regOpt.isPresent()) {
				Registry<Structure> reg = regOpt.get();
				for (Identifier id : reg.keySet()) {
					if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
						suggestions.add(id.getPath());
					}
					suggestions.add(id.toString());
				}
				return suggestions;
			}
		}
		// Fallback common structure names if level registry is not yet loaded
		suggestions.addAll(List.of(
			"ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid",
			"end_city", "fortress", "igloo", "jungle_pyramid", "mansion",
			"mineshaft", "mineshaft_mesa", "monument", "nether_fossil",
			"ocean_ruin_cold", "ocean_ruin_warm", "pillager_outpost",
			"ruined_portal", "shipwreck", "stronghold", "swamp_hut",
			"trail_ruins", "trial_chambers", "village_desert", "village_plains",
			"village_savanna", "village_snowy", "village_taiga"
		));
		return suggestions;
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
			ModMessageUtil.sendAlert(
				Component.literal("Failed to search for structure '" + requestedId + "': " + throwable.getMessage()),
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
				int y = estimateStructureY(level, verifiedPos.getX(), verifiedPos.getZ());
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
					int y = estimateStructureY(level, verifiedPos.getX(), verifiedPos.getZ());
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

	private static int estimateStructureY(ServerLevel level, int blockX, int blockZ) {
		int chunkX = SectionPos.blockToSectionCoord(blockX);
		int chunkZ = SectionPos.blockToSectionCoord(blockZ);
		if (level.hasChunk(chunkX, chunkZ)) {
			int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
			if (surfaceY > level.getMinY()) {
				return surfaceY;
			}
		}
		return 64;
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
		ChunkGeneratorStructureState structureState = ChunkGeneratorStructureState.createForNormal(
			randomState,
			seed,
			biomeSource,
			structureSetLookup
		);
		structureState.ensureStructuresGenerated();

		double minSq = (double) minDistance * (double) minDistance;
		double maxSq = (double) maxDistance * (double) maxDistance;
		LocatedStructureResult best = null;
		double bestSq = Double.MAX_VALUE;

		int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
		int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

		for (Holder.Reference<Structure> holder : matchingHolders) {
			List<StructurePlacement> placements = structureState.getPlacementsForStructure(holder);
			for (StructurePlacement placement : placements) {
				if (placement instanceof ConcentricRingsStructurePlacement concentric) {
					List<ChunkPos> rings = structureState.getRingPositionsFor(concentric);
					if (rings != null) {
						for (ChunkPos cp : rings) {
							BlockPos locatePos = concentric.getLocatePos(cp);
							double dSq = horizontalDistSq(origin, locatePos);
							if (dSq >= minSq && dSq <= maxSq && dSq < bestSq) {
								bestSq = dSq;
								best = new LocatedStructureResult(
									new BlockPos(locatePos.getX(), 64, locatePos.getZ()),
									holder.key().identifier(),
									formatStructureDisplayName(holder.key().identifier()),
									Math.sqrt(dSq),
									dimensionId
								);
							}
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
								if (dSq < minSq || dSq > maxSq || dSq >= bestSq) {
									continue;
								}
								if (!randomSpread.isStructureChunk(structureState, cp.x(), cp.z())) {
									continue;
								}
								Holder<Biome> chunkBiome = biomeSource.getNoiseBiome(
									QuartPos.fromBlock(locatePos.getX()),
									QuartPos.fromBlock(64),
									QuartPos.fromBlock(locatePos.getZ()),
									randomState.sampler()
								);
								if (holder.value().biomes().contains(chunkBiome)) {
									bestSq = dSq;
									best = new LocatedStructureResult(
										new BlockPos(locatePos.getX(), 64, locatePos.getZ()),
										holder.key().identifier(),
										formatStructureDisplayName(holder.key().identifier()),
										Math.sqrt(dSq),
										dimensionId
									);
								}
							}
						}
					}
				}
			}
		}

		return best;
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
		ResourceKey<Structure> exactKey = ResourceKey.create(Registries.STRUCTURE, requestedId);
		Optional<Holder.Reference<Structure>> exact = lookup.get(exactKey);
		if (exact.isPresent()) {
			results.add(exact.get());
			return results;
		}

		String prefix = requestedId.getPath().toLowerCase(Locale.ROOT);
		lookup.listElements().forEach(holder -> {
			Identifier id = holder.key().identifier();
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (path.equals(prefix) || path.startsWith(prefix + "_")) {
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
