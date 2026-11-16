package com.drakonsqoltweaks.client.command;

import com.drakonsqoltweaks.client.DrakonsQolTweaksClient;
import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.config.StructureReturnMode;
import com.drakonsqoltweaks.client.feature.AdminActionHandler;
import com.drakonsqoltweaks.client.feature.BreedingTimerManager;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.feature.StructureLocator;
import com.drakonsqoltweaks.client.gui.DrakonsEnchantScreen;
import com.drakonsqoltweaks.client.gui.DrakonsQolConfigScreen;
import com.drakonsqoltweaks.client.gui.DrakonsSummonScreen;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;

public final class DrakonsQolCommands {
	private static final SuggestionProvider<FabricClientCommandSource> STRUCTURE_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(StructureLocator.getStructureSuggestions(), builder);

	private static final SuggestionProvider<FabricClientCommandSource> RETURN_DETERMINANT_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(List.of("journeymap", "raw", "waypoint", "coords"), builder);

	private static final SuggestionProvider<FabricClientCommandSource> MIN_DISTANCE_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(List.of("0", "100", "250", "500", "1000"), builder);

	private static final SuggestionProvider<FabricClientCommandSource> MAX_DISTANCE_OR_RETURN_SUGGESTIONS = (context, builder) ->
		SharedSuggestionProvider.suggest(List.of("2000", "5000", "10000", "journeymap", "raw"), builder);

	private DrakonsQolCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerRoot(dispatcher, "dqol");
			registerRoot(dispatcher, "drakonsqol");
		});
	}

	private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher, String literal) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(literal)
			.requires(source -> true)
			.executes(ctx -> {
				DrakonsQolTweaksClient.scheduleOpenScreen(new DrakonsQolConfigScreen(null));
				return Command.SINGLE_SUCCESS;
			})
			// /dqol enchant — Opens the SkyBlock-style Custom Enchanting UI (Singleplayer or Admin)
			.then(ClientCommands.literal("enchant")
				.executes(ctx -> {
					Minecraft client = Minecraft.getInstance();
					if (!AdminActionHandler.checkPermissionOrNotify(client)) {
						return 0;
					}
					DrakonsQolTweaksClient.scheduleOpenScreen(new DrakonsEnchantScreen(null));
					return Command.SINGLE_SUCCESS;
				})
			)
			// /dqol anvil — Opens the Anvil UI anywhere (Singleplayer or Admin)
			.then(ClientCommands.literal("anvil")
				.executes(ctx -> {
					AdminActionHandler.openAnvil(Minecraft.getInstance());
					return Command.SINGLE_SUCCESS;
				})
			)
			// /dqol craft — Opens the 3x3 Crafting Table UI anywhere (Singleplayer or Admin)
			.then(ClientCommands.literal("craft")
				.executes(ctx -> {
					AdminActionHandler.openCraftingTable(Minecraft.getInstance());
					return Command.SINGLE_SUCCESS;
				})
			)
			// /dqol summon — Opens the Entity Spawner, Equipment & Attribute Editor UI (Singleplayer or Admin)
			.then(ClientCommands.literal("summon")
				.executes(ctx -> {
					Minecraft client = Minecraft.getInstance();
					if (!AdminActionHandler.checkPermissionOrNotify(client)) {
						return 0;
					}
					DrakonsQolTweaksClient.scheduleOpenScreen(new DrakonsSummonScreen(null));
					return Command.SINGLE_SUCCESS;
				})
			)
			// /dqol s {structure name} [minimum distance] [maximum distance] [return determinant]
			.then(ClientCommands.literal("s")
				.then(ClientCommands.argument("structure", IdentifierArgument.id())
					.suggests(STRUCTURE_SUGGESTIONS)
					// 0) /dqol s <structure> (defaults minDistance to 0)
					.executes(ctx -> {
						Identifier structureId = ctx.getArgument("structure", Identifier.class);
						DrakonsQolConfig config = DrakonsQolConfig.getInstance();
						int maxDistance = Math.max(500, config.getDefaultStructureMaxDistance());
						StructureLocator.locateStructureAsync(
							structureId,
							0,
							maxDistance,
							config.getDefaultStructureReturnMode()
						);
						return Command.SINGLE_SUCCESS;
					})
					.then(ClientCommands.argument("minDistance", IntegerArgumentType.integer(0, 100000))
						.suggests(MIN_DISTANCE_SUGGESTIONS)
						// 1) /dqol s <structure> <minDistance>
						.executes(ctx -> {
							Identifier structureId = ctx.getArgument("structure", Identifier.class);
							int minDistance = IntegerArgumentType.getInteger(ctx, "minDistance");
							DrakonsQolConfig config = DrakonsQolConfig.getInstance();
							int maxDistance = Math.max(minDistance + 500, config.getDefaultStructureMaxDistance());
							StructureLocator.locateStructureAsync(
								structureId,
								minDistance,
								maxDistance,
								config.getDefaultStructureReturnMode()
							);
							return Command.SINGLE_SUCCESS;
						})
						// 2) /dqol s <structure> <minDistance> <maxDistance> [returnDeterminant]
						.then(ClientCommands.argument("maxDistance", IntegerArgumentType.integer(0, 100000))
							.suggests(MAX_DISTANCE_OR_RETURN_SUGGESTIONS)
							.executes(ctx -> {
								Identifier structureId = ctx.getArgument("structure", Identifier.class);
								int minDistance = IntegerArgumentType.getInteger(ctx, "minDistance");
								int maxDistance = IntegerArgumentType.getInteger(ctx, "maxDistance");
								StructureLocator.locateStructureAsync(
									structureId,
									minDistance,
									maxDistance,
									DrakonsQolConfig.getInstance().getDefaultStructureReturnMode()
								);
								return Command.SINGLE_SUCCESS;
							})
							.then(ClientCommands.argument("returnDeterminant", StringArgumentType.word())
								.suggests(RETURN_DETERMINANT_SUGGESTIONS)
								.executes(ctx -> {
									Identifier structureId = ctx.getArgument("structure", Identifier.class);
									int minDistance = IntegerArgumentType.getInteger(ctx, "minDistance");
									int maxDistance = IntegerArgumentType.getInteger(ctx, "maxDistance");
									String determinantStr = StringArgumentType.getString(ctx, "returnDeterminant");
									return executeWithDeterminant(structureId, minDistance, maxDistance, determinantStr);
								})
							)
						)
						// 3) /dqol s <structure> <minDistance> <returnDeterminant> (when maxDistance is omitted)
						.then(ClientCommands.argument("returnDeterminant", StringArgumentType.word())
							.suggests(RETURN_DETERMINANT_SUGGESTIONS)
							.executes(ctx -> {
								Identifier structureId = ctx.getArgument("structure", Identifier.class);
								int minDistance = IntegerArgumentType.getInteger(ctx, "minDistance");
								DrakonsQolConfig config = DrakonsQolConfig.getInstance();
								int maxDistance = Math.max(minDistance + 500, config.getDefaultStructureMaxDistance());
								String determinantStr = StringArgumentType.getString(ctx, "returnDeterminant");
								return executeWithDeterminant(structureId, minDistance, maxDistance, determinantStr);
							})
						)
					)
					// 4) /dqol s <structure> <returnDeterminant> (when both distances are omitted)
					.then(ClientCommands.argument("returnDeterminant", StringArgumentType.word())
						.suggests(RETURN_DETERMINANT_SUGGESTIONS)
						.executes(ctx -> {
							Identifier structureId = ctx.getArgument("structure", Identifier.class);
							DrakonsQolConfig config = DrakonsQolConfig.getInstance();
							int maxDistance = Math.max(500, config.getDefaultStructureMaxDistance());
							String determinantStr = StringArgumentType.getString(ctx, "returnDeterminant");
							return executeWithDeterminant(structureId, 0, maxDistance, determinantStr);
						})
					)
				)
			)
			// /dqol seed <seed> (for setting world seed when playing on multiplayer servers)
			.then(ClientCommands.literal("seed")
				.then(ClientCommands.argument("worldSeed", LongArgumentType.longArg())
					.executes(ctx -> {
						long seed = LongArgumentType.getLong(ctx, "worldSeed");
						DrakonsQolConfig config = DrakonsQolConfig.getInstance();
						config.setMultiplayerFallbackSeed(seed);
						config.save();
						ModMessageUtil.sendClientInfo(Component.literal("Multiplayer structure search seed set to: " + seed));
						return Command.SINGLE_SUCCESS;
					})
				)
			)
			.then(ClientCommands.literal("config")
				.executes(ctx -> {
					DrakonsQolTweaksClient.scheduleOpenScreen(new DrakonsQolConfigScreen(null));
					return Command.SINGLE_SUCCESS;
				})
			)
			.then(ClientCommands.literal("timers")
				.executes(ctx -> {
					Collection<BreedingTimerManager.BreedingTimerEntry> breeding = BreedingTimerManager.getInstance().getActiveTimers();
					Collection<FurnaceTimerManager.FurnaceTimerEntry> furnaces = FurnaceTimerManager.getInstance().getActiveTimers();
					if (breeding.isEmpty() && furnaces.isEmpty()) {
						ModMessageUtil.sendClientInfo(Component.literal("No active breeding or furnace timers."));
						return Command.SINGLE_SUCCESS;
					}
					for (BreedingTimerManager.BreedingTimerEntry b : breeding) {
						ModMessageUtil.sendClientInfo(Component.literal(
							"Breeding (" + b.animalName() + "): " + ModMessageUtil.formatTicksAsMinutesSeconds(b.remainingTicks())
						));
					}
					for (FurnaceTimerManager.FurnaceTimerEntry f : furnaces) {
						ModMessageUtil.sendClientInfo(Component.literal(
							f.furnaceType() + " (" + f.itemCount() + "x " + f.itemName() + "): "
								+ ModMessageUtil.formatTicksAsMinutesSeconds(f.remainingTicks())
								+ (f.fuelRunsOutFirst() ? " (until fuel runs out)" : " (until done smelting)")
						));
					}
					return Command.SINGLE_SUCCESS;
				})
			)
			.then(ClientCommands.literal("cleartimers")
				.executes(ctx -> {
					BreedingTimerManager.getInstance().clearAll();
					FurnaceTimerManager.getInstance().clearAll();
					ModMessageUtil.sendClientInfo(Component.literal("Cleared all active breeding and furnace timers."));
					return Command.SINGLE_SUCCESS;
				})
			);

		dispatcher.register(root);
	}

	private static int executeWithDeterminant(
		Identifier structureId,
		int minDistance,
		int maxDistance,
		String determinantStr
	) {
		StructureReturnMode mode = StructureReturnMode.fromInput(determinantStr, null);
		if (mode == null) {
			ModMessageUtil.sendAlert(
				Component.literal("Invalid return determinant '" + determinantStr + "'. Use 'journeymap' (or 'waypoint') or 'raw' (or 'coords')."),
				true
			);
			return 0;
		}
		StructureLocator.locateStructureAsync(structureId, minDistance, maxDistance, mode);
		return Command.SINGLE_SUCCESS;
	}
}
