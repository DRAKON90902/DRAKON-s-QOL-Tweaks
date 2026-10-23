package com.drakonsqoltweaks.client.mixin;

import com.drakonsqoltweaks.client.feature.AdminActionHandler;
import com.drakonsqoltweaks.client.feature.BreedingTimerManager;
import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "interact", at = @At("HEAD"))
	private void drakonsqoltweaks$onInteractEntity(
		Player player,
		Entity entity,
		EntityHitResult hitResult,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		BreedingTimerManager.getInstance().onInteractEntity(player, entity, hand);
	}

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void drakonsqoltweaks$onUseItemOnBlock(
		LocalPlayer player,
		InteractionHand hand,
		BlockHitResult hitResult,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (player != null && hitResult != null) {
			FurnaceTimerManager.getInstance().onUseBlock(player.level(), hitResult.getBlockPos());
		}
	}

	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void drakonsqoltweaks$onHandleVirtualContainerInput(
		int containerId,
		int slotNum,
		int buttonNum,
		ContainerInput containerInput,
		Player player,
		CallbackInfo ci
	) {
		if (containerId == AdminActionHandler.VIRTUAL_CONTAINER_ID && player != null && player.containerMenu != null) {
			player.containerMenu.clicked(slotNum, buttonNum, containerInput, player);
			AdminActionHandler.syncEntireInventoryToRemoteServer(Minecraft.getInstance());
			ci.cancel();
		}
	}
}
