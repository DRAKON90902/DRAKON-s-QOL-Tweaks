package com.drakonsqoltweaks.client.gui;

import com.drakonsqoltweaks.client.feature.AdminActionHandler;
import com.drakonsqoltweaks.client.util.ModMessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DrakonsSummonScreen extends Screen {
	private static final EquipmentSlot[] EDITABLE_SLOTS = {
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET,
		EquipmentSlot.MAINHAND,
		EquipmentSlot.OFFHAND
	};

	private static final List<Holder<Attribute>> PRIORITY_ATTRIBUTES = List.of(
		Attributes.MAX_HEALTH,
		Attributes.ATTACK_DAMAGE,
		Attributes.MOVEMENT_SPEED,
		Attributes.SCALE,
		Attributes.ARMOR,
		Attributes.ARMOR_TOUGHNESS,
		Attributes.KNOCKBACK_RESISTANCE,
		Attributes.ATTACK_KNOCKBACK,
		Attributes.FOLLOW_RANGE,
		Attributes.STEP_HEIGHT,
		Attributes.JUMP_STRENGTH,
		Attributes.GRAVITY,
		Attributes.SAFE_FALL_DISTANCE,
		Attributes.FALL_DAMAGE_MULTIPLIER,
		Attributes.FLYING_SPEED,
		Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
		Attributes.BURNING_TIME,
		Attributes.SPAWN_REINFORCEMENTS_CHANCE
	);

	private final Screen parent;

	// Entity selection state
	private final List<EntityType<?>> allEntities = new ArrayList<>();
	private final List<EntityType<?>> filteredEntities = new ArrayList<>();
	private EntityType<?> selectedEntityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"));
	private String entitySearchQuery = "";
	private int entityListScroll = 0;

	// Entity flags & options
	private String customEntityName = "";
	private int spawnCount = 1;
	private boolean persistenceRequired = true;
	private boolean noAi = false;
	private boolean glowing = false;
	private boolean invulnerable = false;
	private boolean baby = false;
	private boolean guaranteedDrops = true;

	// Equipment state
	private EquipmentSlot selectedEquipmentSlot = EquipmentSlot.MAINHAND;
	private final Map<EquipmentSlot, ItemStack> equipmentMap = new EnumMap<>(EquipmentSlot.class);
	private String customItemIdText = "";

	// Attributes dropdown & state
	private final List<Holder<Attribute>> attributeOptions = new ArrayList<>();
	private Holder<Attribute> selectedAttribute = Attributes.MAX_HEALTH;
	private boolean attributeDropdownOpen = false;
	private int attributeDropdownScroll = 0;
	private String attributeValueText = "20.0";
	private final Map<Holder<Attribute>, Double> configuredAttributes = new LinkedHashMap<>();

	// Active widgets
	private EditBox entitySearchBox;
	private EditBox customNameBox;
	private EditBox itemInputBox;
	private EditBox attributeValueBox;

	// Layout coordinates cached for custom click/render regions
	private int panelX;
	private int panelW;
	private int topY;
	private int col1X;
	private int col1W;
	private int col2X;
	private int col2W;
	private int col3X;
	private int col3W;
	private int dropdownX;
	private int dropdownY;
	private int dropdownW;
	private static final int DROPDOWN_MAX_VISIBLE = 7;
	private static final int DROPDOWN_ITEM_H = 15;

	public DrakonsSummonScreen(Screen parent) {
		super(Component.literal("Drakon's Entity Spawner & Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.allEntities.isEmpty()) {
			BuiltInRegistries.ENTITY_TYPE.stream()
				.filter(EntityType::canSummon)
				.sorted(Comparator.comparing(et -> et.getDescription().getString(), String.CASE_INSENSITIVE_ORDER))
				.forEach(this.allEntities::add);
		}

		if (this.attributeOptions.isEmpty()) {
			for (Holder<Attribute> priority : PRIORITY_ATTRIBUTES) {
				if (!this.attributeOptions.contains(priority)) {
					this.attributeOptions.add(priority);
				}
			}
			BuiltInRegistries.ATTRIBUTE.listElements()
				.sorted(Comparator.comparing(DrakonsSummonScreen::getAttributeDisplayName, String.CASE_INSENSITIVE_ORDER))
				.forEach(ref -> {
					if (!this.attributeOptions.contains(ref)) {
						this.attributeOptions.add(ref);
					}
				});
		}

		refreshFilteredEntities();

		this.panelW = Math.min(460, this.width - 12);
		this.panelX = (this.width - this.panelW) / 2;
		this.topY = 20;

		int gap = 6;
		this.col1W = 128;
		this.col2W = 152;
		this.col3W = this.panelW - this.col1W - this.col2W - gap * 4;
		this.col1X = this.panelX + gap;
		this.col2X = this.col1X + this.col1W + gap;
		this.col3X = this.col2X + this.col2W + gap;

		initEntityColumn();
		initEquipmentColumn();
		initAttributeColumn();

		// Bottom Spawn & Close Buttons
		int spawnBtnW = 124;
		int spawnBtnX = this.col3X + this.col3W - spawnBtnW;
		int spawnBtnY = this.height - 26;
		this.addRenderableWidget(Button.builder(
			Component.literal("SPAWN ENTITY").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
			btn -> performSummon()
		).bounds(spawnBtnX, spawnBtnY, spawnBtnW, 20)
			.tooltip(Tooltip.create(Component.literal("Spawn the configured entity with all selected armor, items, and attributes!")))
			.build());

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
			.bounds(this.panelX + this.panelW - 52, 2, 48, 16)
			.build());
	}

	private void initEntityColumn() {
		// Entity search box
		this.entitySearchBox = new EditBox(this.font, this.col1X, this.topY + 12, this.col1W, 15, Component.literal("Search Entity"));
		this.entitySearchBox.setHint(Component.literal("Search entity...").withStyle(ChatFormatting.DARK_GRAY));
		this.entitySearchBox.setValue(this.entitySearchQuery);
		this.entitySearchBox.setResponder(val -> {
			this.entitySearchQuery = val;
			this.entityListScroll = 0;
			refreshFilteredEntities();
		});
		this.addRenderableWidget(this.entitySearchBox);

		// Custom Name Box & Count Controls below the entity list
		int listBottomY = this.topY + 100;
		this.customNameBox = new EditBox(this.font, this.col1X, listBottomY + 11, this.col1W - 46, 15, Component.literal("Custom Name"));
		this.customNameBox.setHint(Component.literal("Name Tag...").withStyle(ChatFormatting.DARK_GRAY));
		this.customNameBox.setValue(this.customEntityName);
		this.customNameBox.setResponder(val -> this.customEntityName = val);
		this.addRenderableWidget(this.customNameBox);

		this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
			this.spawnCount = Math.max(1, this.spawnCount - 1);
			this.rebuildWidgets();
		}).bounds(this.col1X + this.col1W - 44, listBottomY + 10, 14, 16)
			.tooltip(Tooltip.create(Component.literal("Decrease spawn count")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
			this.spawnCount = Math.min(50, this.spawnCount + 1);
			this.rebuildWidgets();
		}).bounds(this.col1X + this.col1W - 14, listBottomY + 10, 14, 16)
			.tooltip(Tooltip.create(Component.literal("Increase spawn count")))
			.build());

		// Compact toggle buttons (2 columns x 3 rows)
		int toggleTopY = listBottomY + 28;
		int halfW = (this.col1W - 2) / 2;
		this.addRenderableWidget(Button.builder(
			formatToggle("Persist", this.persistenceRequired),
			btn -> {
				this.persistenceRequired = !this.persistenceRequired;
				this.rebuildWidgets();
			}
		).bounds(this.col1X, toggleTopY, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Prevent entity from despawning naturally")))
			.build());

		this.addRenderableWidget(Button.builder(
			formatToggle("No AI", this.noAi),
			btn -> {
				this.noAi = !this.noAi;
				this.rebuildWidgets();
			}
		).bounds(this.col1X + halfW + 2, toggleTopY, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Disable entity AI so it stands still")))
			.build());

		this.addRenderableWidget(Button.builder(
			formatToggle("Glow", this.glowing),
			btn -> {
				this.glowing = !this.glowing;
				this.rebuildWidgets();
			}
		).bounds(this.col1X, toggleTopY + 16, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Give the entity a glowing outline")))
			.build());

		this.addRenderableWidget(Button.builder(
			formatToggle("Invuln", this.invulnerable),
			btn -> {
				this.invulnerable = !this.invulnerable;
				this.rebuildWidgets();
			}
		).bounds(this.col1X + halfW + 2, toggleTopY + 16, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Make the entity invulnerable to non-creative damage")))
			.build());

		this.addRenderableWidget(Button.builder(
			formatToggle("Baby", this.baby),
			btn -> {
				this.baby = !this.baby;
				this.rebuildWidgets();
			}
		).bounds(this.col1X, toggleTopY + 32, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Spawn as baby variant (if supported by entity)")))
			.build());

		this.addRenderableWidget(Button.builder(
			formatToggle("100% Drop", this.guaranteedDrops),
			btn -> {
				this.guaranteedDrops = !this.guaranteedDrops;
				this.rebuildWidgets();
			}
		).bounds(this.col1X + halfW + 2, toggleTopY + 32, halfW, 15)
			.tooltip(Tooltip.create(Component.literal("Guarantee 100% drop chance for all equipped armor and held items")))
			.build());
	}

	private void initEquipmentColumn() {
		// Quick Armor Set Preset Buttons
		int presetTopY = this.topY + 56;
		int thirdW = (this.col2W - 4) / 3;

		this.addRenderableWidget(Button.builder(Component.literal("Netherite"), btn -> applyArmorPreset(
			Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
		)).bounds(this.col2X, presetTopY, thirdW, 15)
			.tooltip(Tooltip.create(Component.literal("Equip full Netherite Armor")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Diamond"), btn -> applyArmorPreset(
			Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
		)).bounds(this.col2X + thirdW + 2, presetTopY, thirdW, 15)
			.tooltip(Tooltip.create(Component.literal("Equip full Diamond Armor")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Iron"), btn -> applyArmorPreset(
			Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS
		)).bounds(this.col2X + (thirdW + 2) * 2, presetTopY, this.col2W - (thirdW + 2) * 2, 15)
			.tooltip(Tooltip.create(Component.literal("Equip full Iron Armor")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Golden"), btn -> applyArmorPreset(
			Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS
		)).bounds(this.col2X, presetTopY + 16, thirdW, 15)
			.tooltip(Tooltip.create(Component.literal("Equip full Golden Armor")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Chain"), btn -> applyArmorPreset(
			Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS
		)).bounds(this.col2X + thirdW + 2, presetTopY + 16, thirdW, 15)
			.tooltip(Tooltip.create(Component.literal("Equip full Chainmail Armor")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Clear Gear").withStyle(ChatFormatting.RED), btn -> {
			this.equipmentMap.clear();
			this.rebuildWidgets();
		}).bounds(this.col2X + (thirdW + 2) * 2, presetTopY + 16, this.col2W - (thirdW + 2) * 2, 15)
			.tooltip(Tooltip.create(Component.literal("Clear all armor and held items")))
			.build());

		// Quick Held Weapon Presets (for selected hand/slot)
		int wepTopY = presetTopY + 34;
		int qtrW = (this.col2W - 6) / 4;
		this.addRenderableWidget(Button.builder(Component.literal("Sword"), btn -> setSlotItem(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD)))
			.bounds(this.col2X, wepTopY, qtrW, 15)
			.tooltip(Tooltip.create(Component.literal("Set Mainhand to Netherite Sword")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Bow"), btn -> setSlotItem(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)))
			.bounds(this.col2X + qtrW + 2, wepTopY, qtrW, 15)
			.tooltip(Tooltip.create(Component.literal("Set Mainhand to Bow")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Mace"), btn -> setSlotItem(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE)))
			.bounds(this.col2X + (qtrW + 2) * 2, wepTopY, qtrW, 15)
			.tooltip(Tooltip.create(Component.literal("Set Mainhand to Mace")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Shield"), btn -> setSlotItem(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD)))
			.bounds(this.col2X + (qtrW + 2) * 3, wepTopY, this.col2W - (qtrW + 2) * 3, 15)
			.tooltip(Tooltip.create(Component.literal("Set Offhand to Shield")))
			.build());

		// Custom Item ID input for selected slot
		int itemBoxY = wepTopY + 19;
		this.itemInputBox = new EditBox(this.font, this.col2X, itemBoxY, this.col2W - 38, 15, Component.literal("Item ID"));
		this.itemInputBox.setHint(Component.literal("Item ID (e.g. trident)...").withStyle(ChatFormatting.DARK_GRAY));
		this.itemInputBox.setValue(this.customItemIdText);
		this.itemInputBox.setResponder(val -> this.customItemIdText = val);
		this.addRenderableWidget(this.itemInputBox);

		this.addRenderableWidget(Button.builder(Component.literal("Set"), btn -> applyTypedItemToSelectedSlot())
			.bounds(this.col2X + this.col2W - 36, itemBoxY - 1, 36, 17)
			.tooltip(Tooltip.create(Component.literal("Equip typed item ID into the currently selected equipment slot")))
			.build());
	}

	private void initAttributeColumn() {
		this.dropdownX = this.col3X;
		this.dropdownY = this.topY + 12;
		this.dropdownW = this.col3W;

		String attrName = getAttributeDisplayName(this.selectedAttribute);
		String shortAttr = this.font.plainSubstrByWidth(attrName, this.dropdownW - 22);
		Component dropLabel = Component.literal((this.attributeDropdownOpen ? "▲ " : "▼ ") + shortAttr)
			.withStyle(ChatFormatting.YELLOW);

		this.addRenderableWidget(Button.builder(dropLabel, btn -> {
			this.attributeDropdownOpen = !this.attributeDropdownOpen;
			this.rebuildWidgets();
		}).bounds(this.dropdownX, this.dropdownY, this.dropdownW, 18)
			.tooltip(Tooltip.create(Component.literal("Click to open the Attribute Dropdown Menu")))
			.build());

		int valRowY = this.dropdownY + 32;
		this.attributeValueBox = new EditBox(this.font, this.col3X, valRowY, 64, 16, Component.literal("Attribute Value"));
		this.attributeValueBox.setValue(this.attributeValueText);
		this.attributeValueBox.setResponder(val -> this.attributeValueText = val);
		this.addRenderableWidget(this.attributeValueBox);

		this.addRenderableWidget(Button.builder(Component.literal("Set").withStyle(ChatFormatting.GREEN), btn -> applySelectedAttributeValue())
			.bounds(this.col3X + 66, valRowY - 1, 36, 18)
			.tooltip(Tooltip.create(Component.literal("Set this attribute's base value on the entity")))
			.build());

		this.addRenderableWidget(Button.builder(Component.literal("Reset").withStyle(ChatFormatting.RED), btn -> {
			this.configuredAttributes.remove(this.selectedAttribute);
			this.attributeValueText = formatDouble(this.selectedAttribute.value().getDefaultValue());
			this.rebuildWidgets();
		}).bounds(this.col3X + 104, valRowY - 1, Math.max(36, this.col3W - 104), 18)
			.tooltip(Tooltip.create(Component.literal("Remove override for this attribute (restore default)")))
			.build());

		// Quick Attribute Multiplier Buttons
		int multY = valRowY + 19;
		int multW = (this.col3W - 6) / 4;
		double defVal = this.selectedAttribute.value().getDefaultValue();
		double[] multipliers = {0.5, 1.0, 2.0, 5.0};
		String[] multLabels = {"0.5x", "1x Def", "2x", "5x"};
		for (int i = 0; i < 4; i++) {
			double m = multipliers[i];
			int bx = this.col3X + i * (multW + 2);
			int bw = (i == 3) ? (this.col3W - i * (multW + 2)) : multW;
			this.addRenderableWidget(Button.builder(Component.literal(multLabels[i]), btn -> {
				double target = (defVal == 0.0) ? (m * 10.0) : (defVal * m);
				this.attributeValueText = formatDouble(target);
				this.configuredAttributes.put(this.selectedAttribute, target);
				this.rebuildWidgets();
			}).bounds(bx, multY, bw, 15).build());
		}
	}

	private void applyArmorPreset(Item head, Item chest, Item legs, Item feet) {
		this.equipmentMap.put(EquipmentSlot.HEAD, new ItemStack(head));
		this.equipmentMap.put(EquipmentSlot.CHEST, new ItemStack(chest));
		this.equipmentMap.put(EquipmentSlot.LEGS, new ItemStack(legs));
		this.equipmentMap.put(EquipmentSlot.FEET, new ItemStack(feet));
	}

	private void setSlotItem(EquipmentSlot slot, ItemStack stack) {
		this.selectedEquipmentSlot = slot;
		this.equipmentMap.put(slot, stack.copy());
	}

	private void applyTypedItemToSelectedSlot() {
		String raw = this.customItemIdText == null ? "" : this.customItemIdText.trim().toLowerCase(Locale.ROOT);
		if (raw.isEmpty() || raw.equals("air") || raw.equals("none")) {
			this.equipmentMap.remove(this.selectedEquipmentSlot);
			return;
		}
		Identifier id = raw.contains(":") ? Identifier.tryParse(raw) : Identifier.withDefaultNamespace(raw);
		if (id == null) {
			ModMessageUtil.sendAlert(Component.literal("Invalid item ID: " + raw), true);
			return;
		}
		Optional<Holder.Reference<Item>> itemOpt = BuiltInRegistries.ITEM.get(id);
		if (itemOpt.isEmpty() || itemOpt.get().value() == Items.AIR) {
			ModMessageUtil.sendAlert(Component.literal("Unknown item: " + id), true);
			return;
		}
		this.equipmentMap.put(this.selectedEquipmentSlot, new ItemStack(itemOpt.get().value()));
	}

	private void applySelectedAttributeValue() {
		try {
			double val = Double.parseDouble(this.attributeValueText.trim());
			double sanitized = this.selectedAttribute.value().sanitizeValue(val);
			this.configuredAttributes.put(this.selectedAttribute, sanitized);
			this.attributeValueText = formatDouble(sanitized);
			this.rebuildWidgets();
		} catch (NumberFormatException e) {
			ModMessageUtil.sendAlert(Component.literal("Invalid attribute number: '" + this.attributeValueText + "'"), true);
		}
	}

	private void performSummon() {
		AdminActionHandler.SummonConfig config = new AdminActionHandler.SummonConfig(
			this.selectedEntityType,
			this.customEntityName,
			this.spawnCount,
			this.persistenceRequired,
			this.noAi,
			this.glowing,
			this.invulnerable,
			this.baby,
			this.guaranteedDrops,
			new EnumMap<>(this.equipmentMap),
			new LinkedHashMap<>(this.configuredAttributes)
		);
		AdminActionHandler.summonEntities(this.minecraft, config);
	}

	private void refreshFilteredEntities() {
		this.filteredEntities.clear();
		String q = this.entitySearchQuery == null ? "" : this.entitySearchQuery.trim().toLowerCase(Locale.ROOT);
		for (EntityType<?> et : this.allEntities) {
			if (!q.isEmpty()) {
				String name = et.getDescription().getString().toLowerCase(Locale.ROOT);
				String id = EntityType.getKey(et).toString().toLowerCase(Locale.ROOT);
				if (!name.contains(q) && !id.contains(q)) {
					continue;
				}
			}
			this.filteredEntities.add(et);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) { // ENTER
			if (this.attributeValueBox != null && this.attributeValueBox.isFocused()) {
				applySelectedAttributeValue();
				return true;
			}
			if (this.itemInputBox != null && this.itemInputBox.isFocused()) {
				applyTypedItemToSelectedSlot();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
		if (delta != 0) {
			// 1. If Attribute Dropdown is open and mouse is over it, scroll the dropdown
			if (this.attributeDropdownOpen) {
				int listTop = this.dropdownY + 18;
				int listH = DROPDOWN_MAX_VISIBLE * DROPDOWN_ITEM_H;
				if (mouseX >= this.dropdownX && mouseX <= this.dropdownX + this.dropdownW && mouseY >= listTop && mouseY <= listTop + listH) {
					int maxScroll = Math.max(0, this.attributeOptions.size() - DROPDOWN_MAX_VISIBLE);
					this.attributeDropdownScroll = Math.clamp(this.attributeDropdownScroll + delta, 0, maxScroll);
					return true;
				}
			}

			// 2. If mouse is over the Entity List box, scroll the entity list
			int entListTop = this.topY + 29;
			int entListH = 5 * 13;
			if (mouseX >= this.col1X && mouseX <= this.col1X + this.col1W && mouseY >= entListTop && mouseY <= entListTop + entListH) {
				int maxScroll = Math.max(0, this.filteredEntities.size() - 5);
				this.entityListScroll = Math.clamp(this.entityListScroll + delta, 0, maxScroll);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();

		// 1. Priority: Handle clicks inside the open Attribute Dropdown menu first!
		if (this.attributeDropdownOpen) {
			int listTop = this.dropdownY + 18;
			int visibleCount = Math.min(DROPDOWN_MAX_VISIBLE, this.attributeOptions.size());
			int listH = visibleCount * DROPDOWN_ITEM_H;
			if (mx >= this.dropdownX && mx <= this.dropdownX + this.dropdownW && my >= listTop && my < listTop + listH) {
				int clickedRow = (int) ((my - listTop) / DROPDOWN_ITEM_H);
				int idx = this.attributeDropdownScroll + clickedRow;
				if (idx >= 0 && idx < this.attributeOptions.size()) {
					this.selectedAttribute = this.attributeOptions.get(idx);
					Double existingVal = this.configuredAttributes.get(this.selectedAttribute);
					double defaultOrCurrent = existingVal != null ? existingVal : this.selectedAttribute.value().getDefaultValue();
					this.attributeValueText = formatDouble(defaultOrCurrent);
					this.attributeDropdownOpen = false;
					this.rebuildWidgets();
					return true;
				}
			}
			// Clicking outside the dropdown closes it (unless clicking the dropdown button itself)
			if (!(mx >= this.dropdownX && mx <= this.dropdownX + this.dropdownW && my >= this.dropdownY && my < this.dropdownY + 18)) {
				this.attributeDropdownOpen = false;
				this.rebuildWidgets();
				return true;
			}
		}

		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		// 2. Check Entity List click
		int entListTop = this.topY + 29;
		int entVisible = 5;
		int entRowH = 13;
		if (mx >= this.col1X && mx < this.col1X + this.col1W && my >= entListTop && my < entListTop + entVisible * entRowH) {
			int row = (int) ((my - entListTop) / entRowH);
			int idx = this.entityListScroll + row;
			if (idx >= 0 && idx < this.filteredEntities.size()) {
				this.selectedEntityType = this.filteredEntities.get(idx);
				return true;
			}
		}

		// 3. Check 6 Equipment Slots click (Left-click selects, Right-click clears)
		int eqStartY = this.topY + 14;
		int eqSlotGap = 24;
		for (int i = 0; i < EDITABLE_SLOTS.length; i++) {
			int sx = this.col2X + 4 + i * eqSlotGap;
			int sy = eqStartY;
			if (mx >= sx && mx < sx + 20 && my >= sy && my < sy + 20) {
				this.selectedEquipmentSlot = EDITABLE_SLOTS[i];
				if (event.button() == 1) { // Right-click clears slot
					this.equipmentMap.remove(EDITABLE_SLOTS[i]);
				}
				return true;
			}
		}

		// 4. Check Active Configured Attributes list click (click to remove or select)
		int activeAttrTop = this.dropdownY + 74;
		int rowIdx = 0;
		for (Holder<Attribute> attr : new ArrayList<>(this.configuredAttributes.keySet())) {
			int ry = activeAttrTop + rowIdx * 12;
			if (ry + 12 > this.topY + 152) {
				break;
			}
			if (mx >= this.col3X && mx < this.col3X + this.col3W && my >= ry && my < ry + 12) {
				if (mx >= this.col3X + this.col3W - 18) {
					this.configuredAttributes.remove(attr);
				} else {
					this.selectedAttribute = attr;
					this.attributeValueText = formatDouble(this.configuredAttributes.get(attr));
				}
				this.rebuildWidgets();
				return true;
			}
			rowIdx++;
		}

		// 5. Check Player Inventory Slot click at bottom -> copies item into selectedEquipmentSlot!
		int invSlot = getClickedPlayerInventorySlot((int) mx, (int) my);
		if (invSlot != -1 && this.minecraft != null && this.minecraft.player != null) {
			ItemStack stack = this.minecraft.player.getInventory().getItem(invSlot);
			if (!stack.isEmpty()) {
				this.equipmentMap.put(this.selectedEquipmentSlot, stack.copy());
				return true;
			}
		}

		return false;
	}

	private int getClickedPlayerInventorySlot(int mx, int my) {
		int invTop = this.height - 46;
		int invStartX = this.panelX + 6;
		int slotSize = 18;

		// Hotbar row (0..8) + First main inventory row (9..17) side by side or stacked
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 18; col++) {
				int slotIndex = row * 18 + col;
				if (slotIndex >= 36) break;
				int sx = invStartX + col * slotSize;
				int sy = invTop + row * slotSize;
				if (mx >= sx && mx < sx + slotSize && my >= sy && my < sy + slotSize) {
					return slotIndex;
				}
			}
		}
		return -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int topPanelH = this.height - 72;

		// Main panel backdrop
		graphics.fill(this.panelX, this.topY - 2, this.panelX + this.panelW, this.topY + topPanelH, 0xCC10121A);
		graphics.outline(this.panelX, this.topY - 2, this.panelW, topPanelH + 2, 0xFF3A4B6E);

		// Column dividers
		graphics.fill(this.col2X - 3, this.topY + 2, this.col2X - 2, this.topY + topPanelH - 2, 0xFF333344);
		graphics.fill(this.col3X - 3, this.topY + 2, this.col3X - 2, this.topY + topPanelH - 2, 0xFF333344);

		// Screen Title
		graphics.centeredText(this.font, this.title, this.width / 2, 5, 0xFF55FF55);

		// --- Column 1: Entity Selector ---
		graphics.text(this.font, Component.literal("1. Entity: ").append(
			this.selectedEntityType.getDescription().copy().withStyle(ChatFormatting.AQUA)
		), this.col1X, this.topY + 2, 0xFFFFFFFF);

		int entListTop = this.topY + 29;
		int entVisible = 5;
		int entRowH = 13;
		graphics.fill(this.col1X, entListTop, this.col1X + this.col1W, entListTop + entVisible * entRowH, 0xFF161822);
		graphics.outline(this.col1X, entListTop, this.col1W, entVisible * entRowH, 0xFF44445A);

		for (int i = 0; i < entVisible; i++) {
			int idx = this.entityListScroll + i;
			if (idx >= this.filteredEntities.size()) break;
			EntityType<?> et = this.filteredEntities.get(idx);
			int ry = entListTop + i * entRowH;
			boolean isSel = (et == this.selectedEntityType);
			boolean isHov = mouseX >= this.col1X && mouseX < this.col1X + this.col1W && mouseY >= ry && mouseY < ry + entRowH;
			if (isSel || isHov) {
				graphics.fill(this.col1X + 1, ry, this.col1X + this.col1W - 1, ry + entRowH, isSel ? 0xFF284838 : 0xFF2A2A3C);
			}
			String label = this.font.plainSubstrByWidth(et.getDescription().getString(), this.col1W - 6);
			graphics.text(this.font, Component.literal(label), this.col1X + 3, ry + 2, isSel ? 0xFF55FF55 : 0xFFDDDDDD, false);
		}

		int listBottomY = this.topY + 100;
		graphics.text(this.font, Component.literal("Name & Count:"), this.col1X, listBottomY, 0xFFCCCCCC);
		graphics.centeredText(this.font, Component.literal(String.valueOf(this.spawnCount)), this.col1X + this.col1W - 22, listBottomY + 14, 0xFFFFD700);

		// --- Column 2: Armor & Held Items ---
		graphics.text(this.font, Component.literal("2. Armor & Items ( Slot: " + getShortSlotName(this.selectedEquipmentSlot) + " )"), this.col2X, this.topY + 2, 0xFFFFD700);

		ItemStack hoveredStack = ItemStack.EMPTY;
		int eqStartY = this.topY + 14;
		int eqSlotGap = 24;
		String[] slotTags = {"H", "C", "L", "B", "M", "O"};
		for (int i = 0; i < EDITABLE_SLOTS.length; i++) {
			EquipmentSlot slot = EDITABLE_SLOTS[i];
			int sx = this.col2X + 4 + i * eqSlotGap;
			int sy = eqStartY;
			boolean isSel = (slot == this.selectedEquipmentSlot);
			boolean isHov = mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 20;

			graphics.fill(sx, sy, sx + 20, sy + 20, isSel ? 0xFF2D4A2D : (isHov ? 0xFF34344A : 0xFF1A1A26));
			graphics.outline(sx, sy, 20, 20, isSel ? 0xFF55FF55 : 0xFF555566);
			graphics.centeredText(this.font, Component.literal(slotTags[i]), sx + 10, sy + 22, isSel ? 0xFF55FF55 : 0xFF999999);

			ItemStack eqStack = this.equipmentMap.getOrDefault(slot, ItemStack.EMPTY);
			if (!eqStack.isEmpty()) {
				graphics.item(eqStack, sx + 2, sy + 2);
				graphics.itemDecorations(this.font, eqStack, sx + 2, sy + 2);
				if (isHov) {
					hoveredStack = eqStack;
				}
			}
		}
		graphics.text(this.font, Component.literal("Presets or Click Inv Below:"), this.col2X, this.topY + 46, 0xFFBBBBBB);

		// --- Column 3: Attributes & Dropdown ---
		graphics.text(this.font, Component.literal("3. Entity Attributes:"), this.col3X, this.topY + 2, 0xFF55FFFF);
		double defAttrVal = this.selectedAttribute.value().getDefaultValue();
		graphics.text(
			this.font,
			Component.literal("Base Value (Def: " + formatDouble(defAttrVal) + "):"),
			this.col3X,
			this.dropdownY + 21,
			0xFFCCCCCC
		);

		// Active custom attributes list
		int activeAttrTop = this.dropdownY + 62;
		graphics.text(this.font, Component.literal("Configured Attributes:"), this.col3X, activeAttrTop, 0xFFFFAA00);
		int listY = activeAttrTop + 11;
		if (this.configuredAttributes.isEmpty()) {
			graphics.text(this.font, Component.literal("None (using vanilla defaults)"), this.col3X, listY, 0xFF777777);
		} else {
			int idx = 0;
			for (Map.Entry<Holder<Attribute>, Double> entry : this.configuredAttributes.entrySet()) {
				int ry = listY + idx * 12;
				if (ry + 12 > this.topY + topPanelH - 2) break;
				String name = this.font.plainSubstrByWidth(getAttributeDisplayName(entry.getKey()), this.col3W - 48);
				String line = "• " + name + ": " + formatDouble(entry.getValue());
				graphics.text(this.font, Component.literal(line), this.col3X, ry, 0xFF88FF88, false);
				graphics.text(this.font, Component.literal("[X]").withStyle(ChatFormatting.RED), this.col3X + this.col3W - 16, ry, 0xFFFF5555, false);
				idx++;
			}
		}

		// Render standard widgets
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// Render Bottom Player Inventory Strip (for clicking items into selected equipment slot)
		ItemStack invHover = renderBottomInventoryStrip(graphics, mouseX, mouseY);
		if (!invHover.isEmpty()) {
			hoveredStack = invHover;
		}

		// If the Attribute Dropdown is open, render it on the next stratum so it draws ON TOP of all widgets!
		if (this.attributeDropdownOpen) {
			graphics.nextStratum();
			renderAttributeDropdownOverlay(graphics, mouseX, mouseY);
		} else if (!hoveredStack.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, hoveredStack, mouseX, mouseY);
		}
	}

	private void renderAttributeDropdownOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int listTop = this.dropdownY + 18;
		int visibleCount = Math.min(DROPDOWN_MAX_VISIBLE, this.attributeOptions.size());
		int listH = visibleCount * DROPDOWN_ITEM_H;

		graphics.fill(this.dropdownX, listTop, this.dropdownX + this.dropdownW, listTop + listH, 0xF2121420);
		graphics.outline(this.dropdownX, listTop, this.dropdownW, listH, 0xFFFFAA00);

		for (int i = 0; i < visibleCount; i++) {
			int idx = this.attributeDropdownScroll + i;
			if (idx >= this.attributeOptions.size()) break;
			Holder<Attribute> holder = this.attributeOptions.get(idx);
			int ry = listTop + i * DROPDOWN_ITEM_H;

			boolean isHovered = mouseX >= this.dropdownX && mouseX < this.dropdownX + this.dropdownW && mouseY >= ry && mouseY < ry + DROPDOWN_ITEM_H;
			boolean isSelected = holder.equals(this.selectedAttribute);
			boolean isConfigured = this.configuredAttributes.containsKey(holder);

			if (isHovered || isSelected) {
				graphics.fill(this.dropdownX + 1, ry, this.dropdownX + this.dropdownW - 1, ry + DROPDOWN_ITEM_H, isHovered ? 0xFF3A4A6A : 0xFF253545);
			}

			String name = getAttributeDisplayName(holder);
			String suffix = isConfigured
				? " [" + formatDouble(this.configuredAttributes.get(holder)) + "]"
				: " (" + formatDouble(holder.value().getDefaultValue()) + ")";
			String clipped = this.font.plainSubstrByWidth(name + suffix, this.dropdownW - 8);
			int textColor = isConfigured ? 0xFF55FF55 : (isSelected ? 0xFFFFEE55 : 0xFFEEEEEE);
			graphics.text(this.font, Component.literal(clipped), this.dropdownX + 4, ry + 3, textColor, false);
		}
	}

	private ItemStack renderBottomInventoryStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ItemStack hovered = ItemStack.EMPTY;
		if (this.minecraft == null || this.minecraft.player == null) {
			return hovered;
		}
		Inventory inv = this.minecraft.player.getInventory();
		int invTop = this.height - 46;
		int invStartX = this.panelX + 6;
		int slotSize = 18;

		graphics.text(
			this.font,
			Component.literal("Click any Inventory Item below to equip into " + getShortSlotName(this.selectedEquipmentSlot) + " (Right-click slot above to clear):"),
			invStartX,
			invTop - 10,
			0xFFCCCCCC
		);

		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 18; col++) {
				int slotIndex = row * 18 + col;
				if (slotIndex >= 36) break;
				int sx = invStartX + col * slotSize;
				int sy = invTop + row * slotSize;
				boolean isHov = mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize;

				graphics.fill(sx, sy, sx + slotSize, sy + slotSize, isHov ? 0xFF383850 : 0xFF181824);
				graphics.outline(sx, sy, slotSize, slotSize, isHov ? 0xFFFFFFFF : 0xFF404055);

				ItemStack stack = inv.getItem(slotIndex);
				if (!stack.isEmpty()) {
					graphics.item(stack, sx + 1, sy + 1);
					graphics.itemDecorations(this.font, stack, sx + 1, sy + 1);
					if (isHov) {
						hovered = stack;
					}
				}
			}
		}
		return hovered;
	}

	private static String getAttributeDisplayName(Holder<Attribute> holder) {
		String translated = Component.translatable(holder.value().getDescriptionId()).getString();
		if (translated != null && !translated.startsWith("attribute.name.")) {
			return translated;
		}
		return holder.unwrapKey()
			.map(rk -> rk.identifier().getPath().replace('_', ' '))
			.orElse("Attribute");
	}

	private static String getShortSlotName(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "Helmet";
			case CHEST -> "Chestplate";
			case LEGS -> "Leggings";
			case FEET -> "Boots";
			case MAINHAND -> "Main Hand";
			case OFFHAND -> "Offhand";
			default -> slot.getSerializedName();
		};
	}

	private static Component formatToggle(String label, boolean enabled) {
		return Component.literal(label + ": " + (enabled ? "ON" : "OFF"))
			.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	private static String formatDouble(double val) {
		if (Math.abs(val - Math.round(val)) < 0.0001) {
			return String.format(Locale.ROOT, "%.1f", val);
		}
		return String.format(Locale.ROOT, "%.2f", val);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
