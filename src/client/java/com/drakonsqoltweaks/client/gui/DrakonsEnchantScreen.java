package com.drakonsqoltweaks.client.gui;

import com.drakonsqoltweaks.client.feature.AdminActionHandler;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DrakonsEnchantScreen extends Screen {
	private static final int GRID_COLS = 3;
	private static final int GRID_ROWS = 4;
	private static final int ENCHANTS_PER_PAGE = GRID_COLS * GRID_ROWS;

	private final Screen parent;
	private final List<Holder.Reference<Enchantment>> allEnchantments = new ArrayList<>();
	private final List<Holder.Reference<Enchantment>> filteredEnchantments = new ArrayList<>();

	private int insertedInventorySlot = -1;
	private boolean onlyCompatibleFilter = false;
	private String searchQuery = "";
	private int currentPage = 0;

	private Holder.Reference<Enchantment> selectedEnchantment = null;
	private String customLevelText = "5";

	private EditBox searchBox;
	private EditBox customLevelBox;

	private final ItemStack enchantedBookIcon = new ItemStack(Items.ENCHANTED_BOOK);
	private final ItemStack enchantingTableIcon = new ItemStack(Items.ENCHANTING_TABLE);

	public DrakonsEnchantScreen(Screen parent) {
		super(Component.literal("Drakon's SkyBlock Enchanter (0 XP Cost)"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.allEnchantments.isEmpty() && this.minecraft != null && this.minecraft.level != null) {
			Registry<Enchantment> registry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			registry.listElements()
				.sorted(Comparator.comparing(ref -> ref.value().description().getString(), String.CASE_INSENSITIVE_ORDER))
				.forEach(this.allEnchantments::add);
		}

		// Auto-insert held mainhand item on first open if not empty
		if (this.insertedInventorySlot == -1 && this.minecraft != null && this.minecraft.player != null) {
			int selectedHotbar = this.minecraft.player.getInventory().getSelectedSlot();
			if (!this.minecraft.player.getInventory().getItem(selectedHotbar).isEmpty()) {
				this.insertedInventorySlot = selectedHotbar;
			}
		}

		refreshFilteredEnchantments();

		int panelW = Math.min(440, this.width - 16);
		int panelX = (this.width - panelW) / 2;
		int topY = 22;

		int leftColX = panelX + 6;
		int leftColW = 122;

		int mainX = leftColX + leftColW + 8;
		int mainW = panelW - leftColW - 20;

		// Left column action buttons under the item slot
		int leftBtnY = topY + 68;
		Component filterLabel = Component.literal(this.onlyCompatibleFilter ? "Filter: Compatible" : "Filter: All Enchants");
		this.addRenderableWidget(Button.builder(filterLabel, btn -> {
			this.onlyCompatibleFilter = !this.onlyCompatibleFilter;
			this.currentPage = 0;
			refreshFilteredEnchantments();
			this.rebuildWidgets();
		}).bounds(leftColX, leftBtnY, leftColW, 18)
			.tooltip(Tooltip.create(Component.literal("Toggle between showing every enchantment in Minecraft or only vanilla-compatible enchantments for this item")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Max Vanilla All"), btn -> applyMaxVanillaCompatible())
			.bounds(leftColX, leftBtnY + 20, leftColW, 18)
			.tooltip(Tooltip.create(Component.literal("Apply the maximum vanilla level of all compatible enchantments to the inserted item (0 XP)")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Clear All Enchants").withStyle(ChatFormatting.RED), btn -> clearAllEnchantments())
			.bounds(leftColX, leftBtnY + 40, leftColW, 18)
			.tooltip(Tooltip.create(Component.literal("Remove all enchantments from the currently inserted item")))
			.build());

		if (this.selectedEnchantment == null) {
			initEnchantGridWidgets(mainX, topY, mainW);
		} else {
			initEnchantDetailWidgets(mainX, topY, mainW);
		}

		// Close button in top-right corner
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
			.bounds(panelX + panelW - 54, 4, 50, 16)
			.build());
	}

	private void initEnchantGridWidgets(int mainX, int topY, int mainW) {
		int searchW = mainW - 76;
		this.searchBox = new EditBox(this.font, mainX, topY + 2, searchW, 16, Component.literal("Search Enchants"));
		this.searchBox.setHint(Component.literal("Search enchantments...").withStyle(ChatFormatting.DARK_GRAY));
		this.searchBox.setValue(this.searchQuery);
		this.searchBox.setResponder(val -> {
			this.searchQuery = val;
			this.currentPage = 0;
			refreshFilteredEnchantments();
			this.rebuildWidgets();
		});
		this.addRenderableWidget(this.searchBox);

		int maxPage = Math.max(0, (this.filteredEnchantments.size() - 1) / ENCHANTS_PER_PAGE);
		Button prevBtn = Button.builder(Component.literal("<"), btn -> {
			if (this.currentPage > 0) {
				this.currentPage--;
				this.rebuildWidgets();
			}
		}).bounds(mainX + searchW + 4, topY + 1, 20, 18).build();
		prevBtn.active = this.currentPage > 0;
		this.addRenderableWidget(prevBtn);

		Button nextBtn = Button.builder(Component.literal(">"), btn -> {
			if (this.currentPage < maxPage) {
				this.currentPage++;
				this.rebuildWidgets();
			}
		}).bounds(mainX + mainW - 20, topY + 1, 20, 18).build();
		nextBtn.active = this.currentPage < maxPage;
		this.addRenderableWidget(nextBtn);

		int gridTop = topY + 22;
		int gap = 4;
		int cardW = (mainW - gap * (GRID_COLS - 1)) / GRID_COLS;
		int cardH = 24;

		ItemStack currentStack = getInsertedItem();
		int startIdx = this.currentPage * ENCHANTS_PER_PAGE;
		for (int i = 0; i < ENCHANTS_PER_PAGE; i++) {
			int idx = startIdx + i;
			if (idx >= this.filteredEnchantments.size()) {
				break;
			}
			Holder.Reference<Enchantment> holder = this.filteredEnchantments.get(idx);
			int col = i % GRID_COLS;
			int row = i / GRID_COLS;
			int cardX = mainX + col * (cardW + gap);
			int cardY = gridTop + row * (cardH + gap);

			int currentLvl = getEnchantLevelOnStack(currentStack, holder);
			String name = holder.value().description().getString();
			String shortName = this.font.plainSubstrByWidth(name, cardW - 24);
			Component btnLabel = currentLvl > 0
				? Component.literal("   " + shortName + " " + currentLvl).withStyle(ChatFormatting.AQUA)
				: Component.literal("   " + shortName).withStyle(
					(!currentStack.isEmpty() && holder.value().canEnchant(currentStack))
						? ChatFormatting.YELLOW
						: ChatFormatting.WHITE
				);

			Button cardBtn = Button.builder(btnLabel, btn -> {
				this.selectedEnchantment = holder;
				int defaultLvl = currentLvl > 0 ? currentLvl : holder.value().getMaxLevel();
				this.customLevelText = String.valueOf(defaultLvl);
				this.rebuildWidgets();
			}).bounds(cardX, cardY, cardW, cardH)
				.tooltip(Tooltip.create(Component.literal(
					name + "\nVanilla Max Level: " + holder.value().getMaxLevel()
						+ (currentLvl > 0 ? "\nCurrent Level on Item: " + currentLvl : "")
						+ "\nClick to select level or type a custom level!"
				)))
				.build();
			this.addRenderableWidget(cardBtn);
		}
	}

	private void initEnchantDetailWidgets(int mainX, int topY, int mainW) {
		Holder.Reference<Enchantment> holder = this.selectedEnchantment;
		ItemStack currentStack = getInsertedItem();
		int currentLvl = getEnchantLevelOnStack(currentStack, holder);

		this.addRenderableWidget(Button.builder(Component.literal("<- Back to Enchants"), btn -> {
			this.selectedEnchantment = null;
			this.rebuildWidgets();
		}).bounds(mainX, topY + 1, 108, 18).build());

		// SkyBlock-style preset tier buttons (I through X)
		int tierStartY = topY + 40;
		int tierCols = 5;
		int gap = 4;
		int tierW = (mainW - gap * (tierCols - 1)) / tierCols;
		int tierH = 20;

		for (int tier = 1; tier <= 10; tier++) {
			int targetLevel = tier;
			int col = (tier - 1) % tierCols;
			int row = (tier - 1) / tierCols;
			int x = mainX + col * (tierW + gap);
			int y = tierStartY + row * (tierH + gap);

			boolean isCurrent = (currentLvl == targetLevel);
			boolean isVanillaMax = (targetLevel == holder.value().getMaxLevel());
			ChatFormatting color = isCurrent
				? ChatFormatting.GREEN
				: (isVanillaMax ? ChatFormatting.GOLD : ChatFormatting.WHITE);

			Component label = Component.literal("Lvl " + toRomanOrNumber(targetLevel)).withStyle(color);
			this.addRenderableWidget(Button.builder(label, btn -> {
				this.customLevelText = String.valueOf(targetLevel);
				applyEnchantmentLevel(holder, targetLevel);
			}).bounds(x, y, tierW, tierH)
				.tooltip(Tooltip.create(Component.literal(
					"Apply " + holder.value().description().getString() + " " + targetLevel + "\nCost: 0 XP Levels"
				)))
				.build());
		}

		// Custom Level Input Box (allows higher than vanilla enchants, up to 255)
		int customY = tierStartY + 50;
		this.customLevelBox = new EditBox(this.font, mainX, customY, 76, 18, Component.literal("Custom Level"));
		this.customLevelBox.setMaxLength(5);
		this.customLevelBox.setValue(this.customLevelText);
		this.customLevelBox.setResponder(val -> this.customLevelText = val);
		this.customLevelBox.setTooltip(Tooltip.create(Component.literal("Type any level (1 - 255, can be higher than vanilla max!)")));
		this.addRenderableWidget(this.customLevelBox);

		this.addRenderableWidget(Button.builder(
			Component.literal("Apply Custom Level").withStyle(ChatFormatting.GREEN),
			btn -> applyTypedCustomLevel()
		).bounds(mainX + 80, customY - 1, 114, 20)
			.tooltip(Tooltip.create(Component.literal("Apply the typed enchantment level to the inserted item (0 XP)")))
			.build());

		Button removeBtn = Button.builder(
			Component.literal("Remove").withStyle(ChatFormatting.RED),
			btn -> applyEnchantmentLevel(holder, 0)
		).bounds(mainX + 198, customY - 1, Math.max(52, mainW - 198), 20)
			.tooltip(Tooltip.create(Component.literal("Remove this enchantment from the inserted item")))
			.build();
		removeBtn.active = currentLvl > 0;
		this.addRenderableWidget(removeBtn);
	}

	private void applyTypedCustomLevel() {
		if (this.selectedEnchantment == null) {
			return;
		}
		try {
			int lvl = Integer.parseInt(this.customLevelText.trim());
			if (lvl < 0) {
				ModMessageUtil.sendAlert(Component.literal("Enchantment level must be 0 or higher."), true);
				return;
			}
			int clamped = Math.min(255, lvl);
			applyEnchantmentLevel(this.selectedEnchantment, clamped);
		} catch (NumberFormatException e) {
			ModMessageUtil.sendAlert(Component.literal("Invalid level '" + this.customLevelText + "'. Please enter a number (0-255)."), true);
		}
	}

	private void applyEnchantmentLevel(Holder.Reference<Enchantment> holder, int level) {
		ItemStack current = getInsertedItem();
		if (current.isEmpty()) {
			ModMessageUtil.sendAlert(Component.literal("Click an item in your inventory below to insert it first!"), true);
			return;
		}

		ItemStack updated = current.copy();
		if (updated.is(Items.BOOK) && level > 0) {
			ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK, updated.getCount());
			enchantedBook.applyComponents(updated.getComponentsPatch());
			updated = enchantedBook;
		}

		EnchantmentHelper.updateEnchantments(updated, mutable -> mutable.set(holder, level));
		// Also ensure regular items with custom enchantments have enchantment glint and DataComponents.ENCHANTMENTS
		if (!updated.is(Items.ENCHANTED_BOOK)) {
			ItemEnchantments existing = updated.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(existing);
			mut.set(holder, level);
			updated.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
		}

		AdminActionHandler.updateInventorySlot(this.minecraft, this.insertedInventorySlot, updated);
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.0F);
		}
		this.rebuildWidgets();
	}

	private void applyMaxVanillaCompatible() {
		ItemStack current = getInsertedItem();
		if (current.isEmpty()) {
			ModMessageUtil.sendAlert(Component.literal("Click an item in your inventory below to insert it first!"), true);
			return;
		}
		ItemStack updated = current.copy();
		ItemEnchantments existing = updated.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(existing);
		for (Holder.Reference<Enchantment> holder : this.allEnchantments) {
			if (holder.value().canEnchant(updated)) {
				mut.set(holder, holder.value().getMaxLevel());
			}
		}
		updated.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
		AdminActionHandler.updateInventorySlot(this.minecraft, this.insertedInventorySlot, updated);
		if (this.minecraft != null && this.minecraft.player != null) {
			this.minecraft.player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.9F, 1.1F);
		}
		this.rebuildWidgets();
	}

	private void clearAllEnchantments() {
		ItemStack current = getInsertedItem();
		if (current.isEmpty()) {
			return;
		}
		ItemStack updated = current.copy();
		updated.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (updated.has(DataComponents.STORED_ENCHANTMENTS)) {
			updated.set(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		}
		AdminActionHandler.updateInventorySlot(this.minecraft, this.insertedInventorySlot, updated);
		this.rebuildWidgets();
	}

	private void refreshFilteredEnchantments() {
		this.filteredEnchantments.clear();
		ItemStack current = getInsertedItem();
		String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);

		for (Holder.Reference<Enchantment> holder : this.allEnchantments) {
			if (this.onlyCompatibleFilter && !current.isEmpty() && !holder.value().canEnchant(current) && !current.is(Items.BOOK) && !current.is(Items.ENCHANTED_BOOK)) {
				continue;
			}
			if (!q.isEmpty()) {
				String desc = holder.value().description().getString().toLowerCase(Locale.ROOT);
				String id = holder.key().identifier().toString().toLowerCase(Locale.ROOT);
				if (!desc.contains(q) && !id.contains(q)) {
					continue;
				}
			}
			this.filteredEnchantments.add(holder);
		}
	}

	private ItemStack getInsertedItem() {
		if (this.minecraft == null || this.minecraft.player == null || this.insertedInventorySlot < 0) {
			return ItemStack.EMPTY;
		}
		Inventory inv = this.minecraft.player.getInventory();
		if (this.insertedInventorySlot >= inv.getContainerSize()) {
			return ItemStack.EMPTY;
		}
		return inv.getItem(this.insertedInventorySlot);
	}

	private static int getEnchantLevelOnStack(ItemStack stack, Holder<Enchantment> holder) {
		if (stack.isEmpty()) {
			return 0;
		}
		ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		int lvl = enchants.getLevel(holder);
		if (lvl > 0) {
			return lvl;
		}
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		return stored.getLevel(holder);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) { // ENTER or KP_ENTER
			if (this.selectedEnchantment != null && this.customLevelBox != null && this.customLevelBox.isFocused()) {
				applyTypedCustomLevel();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (this.minecraft == null || this.minecraft.player == null) {
			return false;
		}

		double mx = event.x();
		double my = event.y();

		// Check if user clicked the Enchanting Target Slot to clear/unselect it
		int panelW = Math.min(440, this.width - 16);
		int panelX = (this.width - panelW) / 2;
		int topY = 22;
		int targetSlotX = panelX + 52;
		int targetSlotY = topY + 22;
		if (mx >= targetSlotX && mx < targetSlotX + 24 && my >= targetSlotY && my < targetSlotY + 24) {
			this.insertedInventorySlot = -1;
			refreshFilteredEnchantments();
			this.rebuildWidgets();
			return true;
		}

		// Check if user clicked a slot in the bottom Player Inventory section
		int clickedSlot = getClickedInventorySlot((int) mx, (int) my);
		if (clickedSlot != -1) {
			ItemStack clickedStack = this.minecraft.player.getInventory().getItem(clickedSlot);
			if (!clickedStack.isEmpty()) {
				this.insertedInventorySlot = clickedSlot;
				refreshFilteredEnchantments();
				this.rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	private int getClickedInventorySlot(int mx, int my) {
		int invSectionTop = this.height - 82;
		int invStartX = (this.width / 2) - 110;
		int slotSize = 18;

		// Main inventory (3 rows of 9: slots 9..35)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int sx = invStartX + col * slotSize;
				int sy = invSectionTop + row * slotSize;
				if (mx >= sx && mx < sx + slotSize && my >= sy && my < sy + slotSize) {
					return 9 + row * 9 + col;
				}
			}
		}

		// Hotbar (1 row of 9: slots 0..8)
		int hotbarY = invSectionTop + 3 * slotSize + 4;
		for (int col = 0; col < 9; col++) {
			int sx = invStartX + col * slotSize;
			if (mx >= sx && mx < sx + slotSize && my >= hotbarY && my < hotbarY + slotSize) {
				return col;
			}
		}

		// Armor + Offhand column on the right (slots 39, 38, 37, 36, 40)
		int armorX = invStartX + 9 * slotSize + 8;
		int[] extraSlots = {39, 38, 37, 36, 40};
		for (int i = 0; i < extraSlots.length; i++) {
			int sx = armorX + (i / 4) * (slotSize + 2);
			int sy = invSectionTop + (i % 4) * slotSize;
			if (mx >= sx && mx < sx + slotSize && my >= sy && my < sy + slotSize) {
				return extraSlots[i];
			}
		}

		return -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int panelW = Math.min(440, this.width - 16);
		int panelX = (this.width - panelW) / 2;
		int topY = 22;
		int topPanelH = this.height - 108;

		// Outer SkyBlock-style dark panel background
		graphics.fill(panelX, topY - 2, panelX + panelW, topY + topPanelH, 0xCC10121A);
		graphics.outline(panelX, topY - 2, panelW, topPanelH + 2, 0xFF4A3B7A);

		int leftColX = panelX + 6;
		int leftColW = 122;
		int mainX = leftColX + leftColW + 8;
		int mainW = panelW - leftColW - 20;

		// Left column separator
		graphics.fill(leftColX + leftColW + 3, topY + 2, leftColX + leftColW + 4, topY + topPanelH - 4, 0xFF333344);

		// Title
		graphics.centeredText(this.font, this.title, this.width / 2, 7, 0xFFFFD700);

		// Render Enchanting Table Target Slot
		graphics.text(this.font, Component.literal("Item to Enchant:"), leftColX + 18, topY + 6, 0xFFDDDDDD);
		int targetSlotX = panelX + 52;
		int targetSlotY = topY + 20;
		graphics.item(this.enchantingTableIcon, targetSlotX - 24, targetSlotY + 4);
		graphics.fill(targetSlotX, targetSlotY, targetSlotX + 24, targetSlotY + 24, 0xFF1E1E2E);
		graphics.outline(targetSlotX, targetSlotY, 24, 24, 0xFFFFAA00);

		ItemStack inserted = getInsertedItem();
		ItemStack hoveredStack = ItemStack.EMPTY;

		if (!inserted.isEmpty()) {
			graphics.item(inserted, targetSlotX + 4, targetSlotY + 4);
			graphics.itemDecorations(this.font, inserted, targetSlotX + 4, targetSlotY + 4);
			if (mouseX >= targetSlotX && mouseX < targetSlotX + 24 && mouseY >= targetSlotY && mouseY < targetSlotY + 24) {
				hoveredStack = inserted;
			}
			String itemTitle = this.font.plainSubstrByWidth(inserted.getHoverName().getString(), leftColW - 4);
			graphics.centeredText(this.font, Component.literal(itemTitle).withStyle(ChatFormatting.AQUA), leftColX + leftColW / 2, targetSlotY + 28, 0xFF55FFFF);
		} else {
			graphics.centeredText(this.font, Component.literal("Click item below"), leftColX + leftColW / 2, targetSlotY + 28, 0xFF888888);
		}

		// Render Enchanted Book icons on grid cards or detail header
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (this.selectedEnchantment == null) {
			int maxPage = Math.max(1, (this.filteredEnchantments.size() + ENCHANTS_PER_PAGE - 1) / ENCHANTS_PER_PAGE);
			String pageStr = (this.currentPage + 1) + "/" + maxPage;
			graphics.centeredText(this.font, Component.literal(pageStr), mainX + mainW - 36, topY + 6, 0xFFCCCCCC);

			int gridTop = topY + 22;
			int gap = 4;
			int cardW = (mainW - gap * (GRID_COLS - 1)) / GRID_COLS;
			int cardH = 24;
			int startIdx = this.currentPage * ENCHANTS_PER_PAGE;
			for (int i = 0; i < ENCHANTS_PER_PAGE; i++) {
				int idx = startIdx + i;
				if (idx >= this.filteredEnchantments.size()) {
					break;
				}
				int col = i % GRID_COLS;
				int row = i / GRID_COLS;
				int cardX = mainX + col * (cardW + gap);
				int cardY = gridTop + row * (cardH + gap);
				graphics.item(this.enchantedBookIcon, cardX + 3, cardY + 4);
			}
		} else {
			Holder.Reference<Enchantment> holder = this.selectedEnchantment;
			int currentLvl = getEnchantLevelOnStack(inserted, holder);
			graphics.item(this.enchantedBookIcon, mainX + 114, topY + 2);
			String enchTitle = holder.value().description().getString()
				+ " (Vanilla Max: " + holder.value().getMaxLevel()
				+ (currentLvl > 0 ? " | Active: " + currentLvl : "") + ")";
			graphics.text(this.font, Component.literal(enchTitle).withStyle(ChatFormatting.GOLD), mainX + 134, topY + 6, 0xFFFFAA00);
			graphics.text(this.font, Component.literal("Select a SkyBlock Tier or Type Any Level (0 XP Cost):"), mainX, topY + 26, 0xFFCCCCCC);
			graphics.text(this.font, Component.literal("Custom Level (1 - 255, higher than vanilla allowed):"), mainX, topY + 78, 0xFFAAAAAA);
		}

		// Render Bottom Player Inventory Grid
		ItemStack invHovered = renderPlayerInventory(graphics, mouseX, mouseY);
		if (!invHovered.isEmpty()) {
			hoveredStack = invHovered;
		}

		if (!hoveredStack.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
		}
	}

	private ItemStack renderPlayerInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ItemStack hovered = ItemStack.EMPTY;
		if (this.minecraft == null || this.minecraft.player == null) {
			return hovered;
		}
		Inventory inv = this.minecraft.player.getInventory();
		int invSectionTop = this.height - 82;
		int invStartX = (this.width / 2) - 110;
		int slotSize = 18;

		graphics.fill(invStartX - 6, invSectionTop - 12, invStartX + 11 * slotSize + 16, this.height - 2, 0xCC10121A);
		graphics.outline(invStartX - 6, invSectionTop - 12, 11 * slotSize + 22, 82, 0xFF3A3A52);
		graphics.text(this.font, Component.literal("Click any Inventory Item to Insert:"), invStartX, invSectionTop - 10, 0xFFCCCCCC);

		// Main inventory (9..35)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int slot = 9 + row * 9 + col;
				int sx = invStartX + col * slotSize;
				int sy = invSectionTop + row * slotSize;
				ItemStack st = drawInventorySlot(graphics, inv, slot, sx, sy, mouseX, mouseY);
				if (!st.isEmpty()) hovered = st;
			}
		}

		// Hotbar (0..8)
		int hotbarY = invSectionTop + 3 * slotSize + 4;
		for (int col = 0; col < 9; col++) {
			int sx = invStartX + col * slotSize;
			ItemStack st = drawInventorySlot(graphics, inv, col, sx, hotbarY, mouseX, mouseY);
			if (!st.isEmpty()) hovered = st;
		}

		// Armor + Offhand (39, 38, 37, 36, 40)
		int armorX = invStartX + 9 * slotSize + 8;
		int[] extraSlots = {39, 38, 37, 36, 40};
		for (int i = 0; i < extraSlots.length; i++) {
			int sx = armorX + (i / 4) * (slotSize + 2);
			int sy = invSectionTop + (i % 4) * slotSize;
			ItemStack st = drawInventorySlot(graphics, inv, extraSlots[i], sx, sy, mouseX, mouseY);
			if (!st.isEmpty()) hovered = st;
		}

		return hovered;
	}

	private ItemStack drawInventorySlot(
		GuiGraphicsExtractor graphics,
		Inventory inv,
		int slotIndex,
		int sx,
		int sy,
		int mouseX,
		int mouseY
	) {
		boolean isSelected = (slotIndex == this.insertedInventorySlot);
		boolean isHovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;

		int bgColor = isSelected ? 0xFF2E4F2E : (isHovered ? 0xFF383850 : 0xFF1A1A26);
		int borderColor = isSelected ? 0xFF55FF55 : (isHovered ? 0xFFFFFFFF : 0xFF444455);

		graphics.fill(sx, sy, sx + 18, sy + 18, bgColor);
		graphics.outline(sx, sy, 18, 18, borderColor);

		ItemStack stack = inv.getItem(slotIndex);
		if (!stack.isEmpty()) {
			graphics.item(stack, sx + 1, sy + 1);
			graphics.itemDecorations(this.font, stack, sx + 1, sy + 1);
			if (isHovered) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static String toRomanOrNumber(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
