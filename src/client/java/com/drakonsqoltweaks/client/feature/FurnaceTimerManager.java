package com.drakonsqoltweaks.client.feature;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import com.drakonsqoltweaks.client.mixin.AbstractFurnaceBlockEntityAccessor;
import com.drakonsqoltweaks.client.mixin.AbstractFurnaceMenuAccessor;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FurnaceTimerManager {
	private static final FurnaceTimerManager INSTANCE = new FurnaceTimerManager();

	public static final class FurnaceTimerEntry {
		private final String key;
		private final String furnaceType;
		private final BlockPos furnacePos;
		private final ResourceKey<Level> dimension;
		private String itemName;
		private int itemCount;
		private int lastSeenInputCount;
		private int lastSeenResultCount;
		private int smeltedDuringBatch;
		private int remainingTicks;
		private int totalTicks;
		private boolean fuelRunsOutFirst;
		private int lastOpenContainerId;
		private int ticksSinceMenuClosed;

		public FurnaceTimerEntry(
			String key,
			String furnaceType,
			BlockPos furnacePos,
			ResourceKey<Level> dimension,
			String itemName,
			int itemCount,
			int inputCount,
			int resultCount,
			int remainingTicks,
			boolean fuelRunsOutFirst,
			int containerId
		) {
			this.key = key;
			this.furnaceType = furnaceType;
			this.furnacePos = furnacePos;
			this.dimension = dimension;
			this.itemName = itemName;
			this.itemCount = itemCount;
			this.lastSeenInputCount = inputCount;
			this.lastSeenResultCount = resultCount;
			this.smeltedDuringBatch = 0;
			this.remainingTicks = remainingTicks;
			this.totalTicks = remainingTicks;
			this.fuelRunsOutFirst = fuelRunsOutFirst;
			this.lastOpenContainerId = containerId;
			this.ticksSinceMenuClosed = 0;
		}

		public String key() {
			return this.key;
		}

		public String furnaceType() {
			return this.furnaceType;
		}

		public String itemName() {
			return this.itemName;
		}

		public int itemCount() {
			return this.itemCount;
		}

		public int remainingTicks() {
			return this.remainingTicks;
		}

		public int totalTicks() {
			return this.totalTicks;
		}

		public boolean fuelRunsOutFirst() {
			return this.fuelRunsOutFirst;
		}
	}

	private final Map<String, FurnaceTimerEntry> activeTimers = new LinkedHashMap<>();
	private BlockPos lastClickedFurnacePos = null;
	private ResourceKey<Level> lastClickedDimension = null;
	private int boundContainerId = -1;
	private String boundFurnaceKey = null;
	private BlockPos boundFurnacePos = null;
	private ResourceKey<Level> boundFurnaceDimension = null;
	private int menuOpenTicks = 0;

	private FurnaceTimerManager() {
	}

	public static FurnaceTimerManager getInstance() {
		return INSTANCE;
	}

	public void onUseBlock(Level level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof AbstractFurnaceBlock) {
			this.lastClickedFurnacePos = pos.immutable();
			this.lastClickedDimension = level.dimension();
		}
	}

	public void onClosingMenu(Minecraft client, AbstractFurnaceMenu furnaceMenu) {
		if (client == null || client.level == null || furnaceMenu == null) {
			return;
		}
		if (!DrakonsQolConfig.getInstance().isFurnaceTimerEnabled()) {
			return;
		}
		String key = resolveKeyForMenu(furnaceMenu);
		updateFromOpenFurnace(client, client.level, furnaceMenu, key, true);
	}

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.isPaused()) {
			return;
		}
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isFurnaceTimerEnabled()) {
			this.activeTimers.clear();
			return;
		}

		String currentlyOpenKey = null;
		if (client.player.containerMenu instanceof AbstractFurnaceMenu furnaceMenu) {
			currentlyOpenKey = resolveKeyForMenu(furnaceMenu);
			this.menuOpenTicks++;
			updateFromOpenFurnace(client, client.level, furnaceMenu, currentlyOpenKey, false);
		} else {
			this.boundContainerId = -1;
			this.boundFurnaceKey = null;
			this.boundFurnacePos = null;
			this.boundFurnaceDimension = null;
			this.menuOpenTicks = 0;
		}

		// Tick all background furnace timers that are not currently being synced by an open menu
		Iterator<Map.Entry<String, FurnaceTimerEntry>> it = this.activeTimers.entrySet().iterator();
		while (it.hasNext()) {
			FurnaceTimerEntry entry = it.next().getValue();
			if (!entry.key.equals(currentlyOpenKey)) {
				entry.ticksSinceMenuClosed++;
				if (!syncFromIntegratedServerBlockEntity(client, entry, it)) {
					entry.remainingTicks--;
					if (entry.remainingTicks <= 0) {
						it.remove();
						sendFurnaceAlert(entry);
					}
				}
			}
		}
	}

	private String resolveKeyForMenu(AbstractFurnaceMenu menu) {
		if (menu.containerId == this.boundContainerId && this.boundFurnaceKey != null) {
			return this.boundFurnaceKey;
		}
		this.boundContainerId = menu.containerId;
		this.menuOpenTicks = 0;
		if (this.lastClickedFurnacePos != null && this.lastClickedDimension != null) {
			this.boundFurnacePos = this.lastClickedFurnacePos;
			this.boundFurnaceDimension = this.lastClickedDimension;
			this.boundFurnaceKey = this.lastClickedDimension.identifier() + "@"
				+ this.lastClickedFurnacePos.getX() + ","
				+ this.lastClickedFurnacePos.getY() + ","
				+ this.lastClickedFurnacePos.getZ();
		} else {
			this.boundFurnacePos = null;
			this.boundFurnaceDimension = null;
			this.boundFurnaceKey = "furnace_menu_" + menu.containerId;
		}
		return this.boundFurnaceKey;
	}

	private void updateFromOpenFurnace(
		Minecraft client,
		Level level,
		AbstractFurnaceMenu menu,
		String key,
		boolean isClosing
	) {
		ItemStack inputStack = menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();
		ItemStack fuelStack = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem();
		ItemStack resultStack = menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem();

		AbstractFurnaceMenuAccessor accessor = (AbstractFurnaceMenuAccessor) menu;
		ContainerData data = accessor.getData();

		boolean isFastFurnace = (menu instanceof BlastFurnaceMenu) || (menu instanceof SmokerMenu);
		int defaultCookTime = isFastFurnace ? 100 : 200;
		int fuelDivisor = isFastFurnace ? 2 : 1;
		String furnaceName = getFurnaceTypeName(menu);

		int litTimeRemaining = Math.max(0, data.get(0));
		int cookingProgress = Math.max(0, data.get(2));
		int serverCookTotalTime = data.get(3);

		// In Singleplayer, also check the server BlockEntity so we never miss a 1-tick ContainerData packet update
		AbstractFurnaceBlockEntity serverBe = getServerFurnaceBlockEntity(client, this.boundFurnaceDimension, this.boundFurnacePos);
		if (serverBe != null) {
			ContainerData serverData = ((AbstractFurnaceBlockEntityAccessor) serverBe).getDataAccess();
			litTimeRemaining = Math.max(litTimeRemaining, serverData.get(0));
			cookingProgress = Math.max(cookingProgress, serverData.get(2));
			if (serverData.get(3) > 0) {
				serverCookTotalTime = serverData.get(3);
			}
			ItemStack serverInput = serverBe.getItem(AbstractFurnaceMenu.INGREDIENT_SLOT);
			if (inputStack.isEmpty() && !serverInput.isEmpty() && !isClosing) {
				inputStack = serverInput;
			}
		}

		int cookTimePerItem = serverCookTotalTime > 0 ? serverCookTotalTime : defaultCookTime;
		boolean canSmeltInput = !inputStack.isEmpty() && accessor.invokeCanSmelt(inputStack);
		int inputCount = inputStack.getCount();
		int resultCount = resultStack.getCount();
		int maxStack = inputStack.isEmpty() ? 64 : inputStack.getMaxStackSize();

		int outputSpace = resultStack.isEmpty()
			? maxStack
			: Math.max(0, resultStack.getMaxStackSize() - resultCount);
		int itemsAbleToSmelt = canSmeltInput ? Math.min(inputCount, outputSpace > 0 ? inputCount : 0) : 0;

		int perFuelBurnTicks = 0;
		if (!fuelStack.isEmpty() && level.fuelValues().isFuel(fuelStack)) {
			perFuelBurnTicks = level.fuelValues().burnDuration(fuelStack) / fuelDivisor;
		}
		int totalFuelTicks = litTimeRemaining + (fuelStack.getCount() * perFuelBurnTicks);

		FurnaceTimerEntry existing = this.activeTimers.get(key);
		if (itemsAbleToSmelt <= 0 || totalFuelTicks <= 0) {
			// When a menu is first opened, wait a few ticks for SetContentPacket and SetDataPacket before assuming items were removed
			if (existing != null && this.menuOpenTicks <= 5 && !isClosing) {
				return;
			}
			if (existing != null) {
				if (existing.remainingTicks <= 10 || (inputStack.isEmpty() && resultCount > existing.lastSeenResultCount)) {
					this.activeTimers.remove(key);
					sendFurnaceAlert(existing);
				} else {
					this.activeTimers.remove(key);
				}
			}
			return;
		}

		int effectiveItemsToSmelt = Math.min(inputCount, outputSpace);
		int totalSmeltTicks = Math.max(1, (effectiveItemsToSmelt * cookTimePerItem) - cookingProgress);
		boolean fuelRunsOutFirst = totalFuelTicks < totalSmeltTicks;
		int effectiveTicks = Math.min(totalSmeltTicks, totalFuelTicks);
		String itemName = inputStack.getHoverName().getString();

		if (existing != null) {
			if (!existing.itemName.equals(itemName)) {
				// Switched to smelting a different item
				existing.itemName = itemName;
				existing.lastSeenInputCount = inputCount;
				existing.lastSeenResultCount = resultCount;
				existing.smeltedDuringBatch = 0;
				existing.itemCount = computeInitialBatchCount(inputCount, resultCount, maxStack);
			} else {
				updateBatchCount(existing, inputCount, resultCount, maxStack, existing.lastOpenContainerId == menu.containerId);
			}
			existing.remainingTicks = effectiveTicks;
			if (effectiveTicks > existing.totalTicks) {
				existing.totalTicks = effectiveTicks;
			}
			existing.fuelRunsOutFirst = fuelRunsOutFirst;
			existing.lastOpenContainerId = menu.containerId;
			existing.ticksSinceMenuClosed = 0;
		} else {
			int initialBatchCount = computeInitialBatchCount(inputCount, resultCount, maxStack);
			FurnaceTimerEntry created = new FurnaceTimerEntry(
				key,
				furnaceName,
				this.boundFurnacePos,
				this.boundFurnaceDimension,
				itemName,
				initialBatchCount,
				inputCount,
				resultCount,
				effectiveTicks,
				fuelRunsOutFirst,
				menu.containerId
			);
			if (resultCount > 0 && inputCount + resultCount <= maxStack) {
				created.smeltedDuringBatch = resultCount;
			}
			this.activeTimers.put(key, created);
		}
	}

	private int computeInitialBatchCount(int inputCount, int resultCount, int maxStack) {
		if (resultCount > 0 && inputCount + resultCount <= maxStack) {
			return inputCount + resultCount;
		}
		return inputCount;
	}

	private void updateBatchCount(
		FurnaceTimerEntry entry,
		int currentInputCount,
		int currentResultCount,
		int maxStack,
		boolean sameMenuSession
	) {
		if (currentResultCount > entry.lastSeenResultCount) {
			entry.smeltedDuringBatch += (currentResultCount - entry.lastSeenResultCount);
		} else if (sameMenuSession && currentInputCount < entry.lastSeenInputCount && currentResultCount == entry.lastSeenResultCount) {
			// Player manually removed items from the input slot while the menu was open
			int manuallyRemoved = entry.lastSeenInputCount - currentInputCount;
			entry.itemCount = Math.max(currentInputCount, entry.itemCount - manuallyRemoved);
		}

		int candidateTotal = currentInputCount + entry.smeltedDuringBatch;
		if (currentResultCount > 0 && currentInputCount + currentResultCount <= maxStack) {
			candidateTotal = Math.max(candidateTotal, currentInputCount + currentResultCount);
		}
		entry.itemCount = Math.max(entry.itemCount, Math.max(currentInputCount, candidateTotal));
		if (currentInputCount > entry.lastSeenInputCount && entry.smeltedDuringBatch == 0) {
			entry.itemCount = currentInputCount;
		}

		entry.lastSeenInputCount = currentInputCount;
		entry.lastSeenResultCount = currentResultCount;
	}

	private boolean syncFromIntegratedServerBlockEntity(
		Minecraft client,
		FurnaceTimerEntry entry,
		Iterator<Map.Entry<String, FurnaceTimerEntry>> iterator
	) {
		if (entry.ticksSinceMenuClosed < 10) {
			return false;
		}
		AbstractFurnaceBlockEntity serverBe = getServerFurnaceBlockEntity(client, entry.dimension, entry.furnacePos);
		if (serverBe == null || client.level == null) {
			return false;
		}

		ItemStack inputStack = serverBe.getItem(AbstractFurnaceMenu.INGREDIENT_SLOT);
		ItemStack fuelStack = serverBe.getItem(AbstractFurnaceMenu.FUEL_SLOT);
		ItemStack resultStack = serverBe.getItem(AbstractFurnaceMenu.RESULT_SLOT);
		ContainerData data = ((AbstractFurnaceBlockEntityAccessor) serverBe).getDataAccess();

		boolean isFastFurnace = (serverBe instanceof BlastFurnaceBlockEntity) || (serverBe instanceof SmokerBlockEntity);
		int defaultCookTime = isFastFurnace ? 100 : 200;
		int fuelDivisor = isFastFurnace ? 2 : 1;

		int litTimeRemaining = Math.max(0, data.get(0));
		int cookingProgress = Math.max(0, data.get(2));
		int serverCookTotalTime = data.get(3);
		int cookTimePerItem = serverCookTotalTime > 0 ? serverCookTotalTime : defaultCookTime;

		int inputCount = inputStack.getCount();
		int resultCount = resultStack.getCount();
		int maxStack = inputStack.isEmpty() ? 64 : inputStack.getMaxStackSize();
		int outputSpace = resultStack.isEmpty()
			? maxStack
			: Math.max(0, resultStack.getMaxStackSize() - resultCount);
		int itemsAbleToSmelt = !inputStack.isEmpty() ? Math.min(inputCount, outputSpace) : 0;

		int perFuelBurnTicks = 0;
		if (!fuelStack.isEmpty() && client.level.fuelValues().isFuel(fuelStack)) {
			perFuelBurnTicks = client.level.fuelValues().burnDuration(fuelStack) / fuelDivisor;
		}
		int totalFuelTicks = litTimeRemaining + (fuelStack.getCount() * perFuelBurnTicks);

		if (inputCount > 0) {
			updateBatchCount(entry, inputCount, resultCount, maxStack, false);
		}

		if (itemsAbleToSmelt <= 0 || totalFuelTicks <= 0) {
			iterator.remove();
			sendFurnaceAlert(entry);
			return true;
		}

		int totalSmeltTicks = Math.max(1, (itemsAbleToSmelt * cookTimePerItem) - cookingProgress);
		entry.fuelRunsOutFirst = totalFuelTicks < totalSmeltTicks;
		entry.remainingTicks = Math.min(totalSmeltTicks, totalFuelTicks);
		if (entry.remainingTicks > entry.totalTicks) {
			entry.totalTicks = entry.remainingTicks;
		}
		if (!inputStack.isEmpty()) {
			entry.itemName = inputStack.getHoverName().getString();
		}
		return true;
	}

	private AbstractFurnaceBlockEntity getServerFurnaceBlockEntity(
		Minecraft client,
		ResourceKey<Level> dimension,
		BlockPos pos
	) {
		if (client == null || dimension == null || pos == null) {
			return null;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null) {
			return null;
		}
		ServerLevel serverLevel = server.getLevel(dimension);
		if (serverLevel == null || !serverLevel.hasChunkAt(pos)) {
			return null;
		}
		if (serverLevel.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnaceBe) {
			return furnaceBe;
		}
		return null;
	}

	public FurnaceTimerEntry getEntryForOpenMenu(AbstractFurnaceMenu menu) {
		if (menu == null) {
			return null;
		}
		for (FurnaceTimerEntry entry : this.activeTimers.values()) {
			if (entry.lastOpenContainerId == menu.containerId) {
				return entry;
			}
		}
		return null;
	}

	public static String getFurnaceTypeName(AbstractFurnaceMenu menu) {
		if (menu instanceof BlastFurnaceMenu) {
			return "Blast Furnace";
		}
		if (menu instanceof SmokerMenu) {
			return "Smoker";
		}
		return "Furnace";
	}

	private void sendFurnaceAlert(FurnaceTimerEntry entry) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		String template = entry.fuelRunsOutFirst
			? config.getFurnaceFuelOutMessage()
			: config.getFurnaceDoneMessage();
		String formatted = template
			.replace("{furnace}", entry.furnaceType)
			.replace("{item}", entry.itemName)
			.replace("{items}", entry.itemCount + "x " + entry.itemName)
			.replace("{count}", String.valueOf(entry.itemCount));
		ModMessageUtil.sendAlert(Component.literal(formatted), entry.fuelRunsOutFirst);
	}

	public Collection<FurnaceTimerEntry> getActiveTimers() {
		return Collections.unmodifiableCollection(this.activeTimers.values());
	}

	public void clearAll() {
		this.activeTimers.clear();
	}
}
