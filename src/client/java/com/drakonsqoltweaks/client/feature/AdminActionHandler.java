package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.DrakonsQolTweaksClient;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminActionHandler {
	public static final int VIRTUAL_CONTAINER_ID = -77;

	public record SummonConfig(
		EntityType<?> entityType,
		String customName,
		int count,
		boolean persistenceRequired,
		boolean noAi,
		boolean glowing,
		boolean invulnerable,
		boolean baby,
		boolean guaranteedDrops,
		Map<EquipmentSlot, ItemStack> equipment,
		Map<Holder<Attribute>, Double> attributes
	) {
	}

	private AdminActionHandler() {
	}

	public static boolean canUseAdminOrSingleplayer(Minecraft client) {
		if (client == null || client.player == null) {
			return false;
		}
		if (client.getSingleplayerServer() != null) {
			return true;
		}
		PermissionSet perms = client.player.permissions();
		if (perms != null && (
			perms.hasPermission(Permissions.COMMANDS_GAMEMASTER)
				|| perms.hasPermission(Permissions.COMMANDS_ADMIN)
				|| perms.hasPermission(Permissions.COMMANDS_OWNER)
		)) {
			return true;
		}
		return client.player.getAbilities().instabuild;
	}

	public static boolean checkPermissionOrNotify(Minecraft client) {
		if (canUseAdminOrSingleplayer(client)) {
			return true;
		}
		ModMessageUtil.sendAlert(
			Component.literal("This feature requires playing on Singleplayer or having Admin/OP permissions on the server."),
			true
		);
		return false;
	}

	public static void openAnvil(Minecraft client) {
		if (!checkPermissionOrNotify(client)) {
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server != null && client.player != null) {
			UUID playerUuid = client.player.getUUID();
			server.execute(() -> {
				ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUuid);
				if (serverPlayer == null) {
					return;
				}
				serverPlayer.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new AnvilMenu(syncId, inventory, ContainerLevelAccess.NULL) {
						@Override
						public boolean stillValid(Player player) {
							return true;
						}

						@Override
						protected void onTake(Player player, ItemStack stack) {
							super.onTake(player, stack);
							player.level().levelEvent(1030, player.blockPosition(), 0);
						}

						@Override
						public void removed(Player player) {
							super.removed(player);
							this.clearContainer(player, this.inputSlots);
						}
					},
					Component.translatable("container.repair")
				));
			});
			return;
		}

		// Remote Multiplayer fallback when player has Admin / Creative permissions
		if (client.player != null) {
			Inventory inv = client.player.getInventory();
			AnvilMenu virtualMenu = new AnvilMenu(VIRTUAL_CONTAINER_ID, inv, ContainerLevelAccess.NULL) {
				@Override
				public boolean stillValid(Player player) {
					return true;
				}

				@Override
				protected void onTake(Player player, ItemStack stack) {
					super.onTake(player, stack);
					syncEntireInventoryToRemoteServer(client);
				}

				@Override
				public void removed(Player player) {
					super.removed(player);
					this.clearContainer(player, this.inputSlots);
					syncEntireInventoryToRemoteServer(client);
				}
			};
			client.player.containerMenu = virtualMenu;
			DrakonsQolTweaksClient.scheduleOpenScreen(
				new AnvilScreen(virtualMenu, inv, Component.translatable("container.repair"))
			);
		}
	}

	public static void openCraftingTable(Minecraft client) {
		if (!checkPermissionOrNotify(client)) {
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server != null && client.player != null) {
			UUID playerUuid = client.player.getUUID();
			server.execute(() -> {
				ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUuid);
				if (serverPlayer == null) {
					return;
				}
				ContainerLevelAccess access = ContainerLevelAccess.create(serverPlayer.level(), serverPlayer.blockPosition());
				serverPlayer.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new CraftingMenu(syncId, inventory, access) {
						@Override
						public boolean stillValid(Player player) {
							return true;
						}
					},
					Component.translatable("container.crafting")
				));
			});
			return;
		}

		// Remote Multiplayer fallback when player has Admin / Creative permissions
		if (client.player != null) {
			Inventory inv = client.player.getInventory();
			CraftingMenu virtualMenu = new CraftingMenu(VIRTUAL_CONTAINER_ID, inv, ContainerLevelAccess.NULL) {
				@Override
				public boolean stillValid(Player player) {
					return true;
				}

				@Override
				public void slotsChanged(Container container) {
					super.slotsChanged(container);
				}

				@Override
				public void removed(Player player) {
					super.removed(player);
					this.clearContainer(player, this.craftSlots);
					syncEntireInventoryToRemoteServer(client);
				}
			};
			client.player.containerMenu = virtualMenu;
			DrakonsQolTweaksClient.scheduleOpenScreen(
				new CraftingScreen(virtualMenu, inv, Component.translatable("container.crafting"))
			);
		}
	}

	public static void updateInventorySlot(Minecraft client, int inventorySlot, ItemStack newStack) {
		if (client == null || client.player == null) {
			return;
		}
		ItemStack copy = newStack.copy();
		client.player.getInventory().setItem(inventorySlot, copy);

		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) {
			UUID playerUuid = client.player.getUUID();
			ItemStack serverCopy = newStack.copy();
			server.execute(() -> {
				ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUuid);
				if (serverPlayer != null) {
					serverPlayer.getInventory().setItem(inventorySlot, serverCopy);
					serverPlayer.inventoryMenu.broadcastChanges();
				}
			});
			return;
		}

		// Remote server sync (Creative packet or Admin /item replace command)
		syncSingleSlotToRemoteServer(client, inventorySlot, copy);
	}

	public static void syncEntireInventoryToRemoteServer(Minecraft client) {
		if (client == null || client.player == null || client.getSingleplayerServer() != null) {
			return;
		}
		Inventory inv = client.player.getInventory();
		for (int i = 0; i < 36; i++) {
			syncSingleSlotToRemoteServer(client, i, inv.getItem(i));
		}
	}

	private static void syncSingleSlotToRemoteServer(Minecraft client, int inventorySlot, ItemStack stack) {
		if (client.player == null || client.gameMode == null) {
			return;
		}
		if (client.player.getAbilities().instabuild) {
			int packetSlot = toPlayerInventoryMenuSlot(inventorySlot);
			if (packetSlot != -1) {
				client.gameMode.handleCreativeModeItemAdd(stack.copy(), packetSlot);
			}
			return;
		}

		if (client.player.connection != null) {
			String slotArg = toCommandSlotName(inventorySlot);
			if (slotArg == null) {
				return;
			}
			if (stack.isEmpty()) {
				client.player.connection.sendCommand("item replace entity @s " + slotArg + " with minecraft:air");
			} else {
				Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
				String componentsStr = formatItemEnchantmentComponent(stack);
				client.player.connection.sendCommand(
					"item replace entity @s " + slotArg + " with " + itemId + componentsStr + " " + stack.getCount()
				);
			}
		}
	}

	private static int toPlayerInventoryMenuSlot(int invSlot) {
		if (invSlot >= 0 && invSlot < 9) {
			return 36 + invSlot;
		}
		if (invSlot >= 9 && invSlot < 36) {
			return invSlot;
		}
		if (invSlot == 40) {
			return 45;
		}
		if (invSlot >= 36 && invSlot <= 39) {
			return 8 - (invSlot - 36);
		}
		return -1;
	}

	private static String toCommandSlotName(int invSlot) {
		if (invSlot >= 0 && invSlot < 9) {
			return "hotbar." + invSlot;
		}
		if (invSlot >= 9 && invSlot < 36) {
			return "inventory." + (invSlot - 9);
		}
		if (invSlot == 40) {
			return "weapon.offhand";
		}
		return switch (invSlot) {
			case 36 -> "armor.feet";
			case 37 -> "armor.legs";
			case 38 -> "armor.chest";
			case 39 -> "armor.head";
			default -> null;
		};
	}

	public static String formatItemEnchantmentComponent(ItemStack stack) {
		ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
		StringBuilder sb = new StringBuilder();
		boolean hasEnchants = !enchantments.isEmpty();
		Component customName = stack.get(DataComponents.CUSTOM_NAME);

		if (!hasEnchants && customName == null) {
			return "";
		}

		sb.append("[");
		boolean firstComponent = true;
		if (hasEnchants) {
			sb.append("minecraft:enchantments={");
			boolean first = true;
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
				String key = entry.getKey().unwrapKey()
					.map(rk -> rk.identifier().toString())
					.orElse(null);
				if (key != null && entry.getIntValue() > 0) {
					if (!first) {
						sb.append(",");
					}
					sb.append("\"").append(key).append("\":").append(entry.getIntValue());
					first = false;
				}
			}
			sb.append("}");
			firstComponent = false;
		}
		if (customName != null) {
			if (!firstComponent) {
				sb.append(",");
			}
			String escaped = customName.getString().replace("\\", "\\\\").replace("\"", "\\\"");
			sb.append("minecraft:custom_name=\"").append(escaped).append("\"");
		}
		sb.append("]");
		return sb.toString();
	}

	public static void summonEntities(Minecraft client, SummonConfig config) {
		if (client == null || client.player == null || config == null || config.entityType() == null) {
			return;
		}
		if (!checkPermissionOrNotify(client)) {
			return;
		}

		Vec3 spawnPos = computeSpawnPosition(client);
		int count = Math.clamp(config.count(), 1, 50);

		MinecraftServer server = client.getSingleplayerServer();
		if (server != null) {
			UUID playerUuid = client.player.getUUID();
			server.execute(() -> {
				ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerUuid);
				if (serverPlayer == null) {
					return;
				}
				ServerLevel serverLevel = serverPlayer.level();
				int spawnedCount = 0;

				for (int i = 0; i < count; i++) {
					Entity entity = config.entityType().create(serverLevel, EntitySpawnReason.COMMAND);
					if (entity == null) {
						continue;
					}
					double offsetX = (i == 0) ? 0.0 : (serverLevel.getRandom().nextDouble() - 0.5) * 1.5;
					double offsetZ = (i == 0) ? 0.0 : (serverLevel.getRandom().nextDouble() - 0.5) * 1.5;
					entity.snapTo(
						spawnPos.x + offsetX,
						spawnPos.y,
						spawnPos.z + offsetZ,
						serverPlayer.getYRot() + 180.0F,
						0.0F
					);

					if (entity instanceof Mob mob) {
						mob.finalizeSpawn(
							serverLevel,
							serverLevel.getCurrentDifficultyAt(BlockPos.containing(spawnPos)),
							EntitySpawnReason.COMMAND,
							null
						);
						if (config.persistenceRequired()) {
							mob.setPersistenceRequired();
						}
						mob.setNoAi(config.noAi());
						if (config.baby()) {
							mob.setBaby(true);
						}
					} else if (entity instanceof AgeableMob ageable && config.baby()) {
						ageable.setBaby(true);
					}

					if (entity instanceof Zombie zombie && config.baby()) {
						zombie.setBaby(true);
					}

					if (config.customName() != null && !config.customName().isBlank()) {
						entity.setCustomName(Component.literal(config.customName().trim()));
						entity.setCustomNameVisible(true);
					}
					entity.setGlowingTag(config.glowing());
					entity.setInvulnerable(config.invulnerable());

					if (entity instanceof LivingEntity living) {
						// Apply custom equipment
						for (Map.Entry<EquipmentSlot, ItemStack> eq : config.equipment().entrySet()) {
							EquipmentSlot slot = eq.getKey();
							ItemStack itemStack = eq.getValue();
							if (itemStack != null && !itemStack.isEmpty()) {
								living.setItemSlot(slot, itemStack.copy());
								if (living instanceof Mob mob && config.guaranteedDrops()) {
									mob.setGuaranteedDrop(slot);
								}
							} else if (config.equipment().containsKey(slot)) {
								living.setItemSlot(slot, ItemStack.EMPTY);
							}
						}

						// Apply custom attributes
						for (Map.Entry<Holder<Attribute>, Double> attrEntry : config.attributes().entrySet()) {
							AttributeInstance inst = living.getAttribute(attrEntry.getKey());
							if (inst != null && attrEntry.getValue() != null) {
								inst.setBaseValue(attrEntry.getValue());
							}
						}

						// Ensure entity spawns at full health if MAX_HEALTH was modified
						if (config.attributes().containsKey(Attributes.MAX_HEALTH)) {
							living.setHealth(living.getMaxHealth());
						}
					}

					serverLevel.addFreshEntityWithPassengers(entity);
					spawnedCount++;
				}

				int finalSpawned = spawnedCount;
				client.execute(() -> ModMessageUtil.sendClientInfo(
					Component.literal("Spawned " + finalSpawned + "x " + config.entityType().getDescription().getString() + "!")
				));
			});
			return;
		}

		// Remote Multiplayer fallback using /summon command
		if (client.player.connection != null) {
			Identifier entityId = EntityType.getKey(config.entityType());
			String nbt = buildSummonNbt(config);
			String coords = String.format(Locale.ROOT, "%.2f %.2f %.2f", spawnPos.x, spawnPos.y, spawnPos.z);
			for (int i = 0; i < count; i++) {
				client.player.connection.sendCommand("summon " + entityId + " " + coords + " " + nbt);
			}
			ModMessageUtil.sendClientInfo(
				Component.literal("Sent summon command for " + count + "x " + config.entityType().getDescription().getString() + "!")
			);
		}
	}

	private static Vec3 computeSpawnPosition(Minecraft client) {
		if (client.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos targetPos = blockHit.getBlockPos().relative(blockHit.getDirection());
			return new Vec3(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
		}
		Vec3 look = client.player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
		if (horizontal.lengthSqr() > 0.001) {
			horizontal = horizontal.normalize().scale(2.5);
		}
		return client.player.position().add(horizontal);
	}

	private static String buildSummonNbt(SummonConfig config) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;

		if (config.customName() != null && !config.customName().isBlank()) {
			String escaped = config.customName().trim().replace("\\", "\\\\").replace("\"", "\\\"");
			sb.append("CustomName:\"").append(escaped).append("\",CustomNameVisible:1b");
			first = false;
		}
		if (config.persistenceRequired()) {
			if (!first) sb.append(",");
			sb.append("PersistenceRequired:1b");
			first = false;
		}
		if (config.noAi()) {
			if (!first) sb.append(",");
			sb.append("NoAI:1b");
			first = false;
		}
		if (config.glowing()) {
			if (!first) sb.append(",");
			sb.append("Glowing:1b");
			first = false;
		}
		if (config.invulnerable()) {
			if (!first) sb.append(",");
			sb.append("Invulnerable:1b");
			first = false;
		}
		if (config.baby()) {
			if (!first) sb.append(",");
			sb.append("IsBaby:1b,Age:-24000");
			first = false;
		}

		// Equipment NBT
		boolean hasAnyEquipment = config.equipment().values().stream().anyMatch(s -> s != null && !s.isEmpty());
		if (hasAnyEquipment) {
			if (!first) sb.append(",");
			sb.append("equipment:{");
			boolean firstEq = true;
			for (Map.Entry<EquipmentSlot, ItemStack> entry : config.equipment().entrySet()) {
				ItemStack stack = entry.getValue();
				if (stack != null && !stack.isEmpty()) {
					if (!firstEq) sb.append(",");
					Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
					sb.append(entry.getKey().getSerializedName())
						.append(":{id:\"").append(itemId).append("\",count:").append(stack.getCount()).append("}");
					firstEq = false;
				}
			}
			sb.append("}");
			first = false;
		}

		// Attributes NBT
		if (!config.attributes().isEmpty()) {
			if (!first) sb.append(",");
			sb.append("attributes:[");
			boolean firstAttr = true;
			for (Map.Entry<Holder<Attribute>, Double> attr : config.attributes().entrySet()) {
				String attrId = attr.getKey().unwrapKey()
					.map(rk -> rk.identifier().toString())
					.orElse(null);
				if (attrId != null && attr.getValue() != null) {
					if (!firstAttr) sb.append(",");
					sb.append("{id:\"").append(attrId).append("\",base:")
						.append(String.format(Locale.ROOT, "%.4f", attr.getValue())).append("d}");
					firstAttr = false;
				}
			}
			sb.append("]");
			if (config.attributes().containsKey(Attributes.MAX_HEALTH)) {
				sb.append(",Health:").append(String.format(Locale.ROOT, "%.2f", config.attributes().get(Attributes.MAX_HEALTH))).append("f");
			}
		}

		sb.append("}");
		return sb.toString();
	}
}
