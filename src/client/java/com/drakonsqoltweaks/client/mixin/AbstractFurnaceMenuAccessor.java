package com.drakonsqoltweaks.client.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractFurnaceMenu.class)
public interface AbstractFurnaceMenuAccessor {
	@Accessor("data")
	ContainerData getData();

	@Invoker("canSmelt")
	boolean invokeCanSmelt(ItemStack stack);

	@Invoker("isFuel")
	boolean invokeIsFuel(ItemStack stack);
}
