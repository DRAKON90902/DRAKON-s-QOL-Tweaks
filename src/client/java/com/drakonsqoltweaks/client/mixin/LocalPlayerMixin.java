package com.drakonsqoltweaks.client.mixin;

import com.drakonsqoltweaks.client.feature.FurnaceTimerManager;
import com.drakonsqoltweaks.client.feature.HotbarRefillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "drop", at = @At("HEAD"))
	private void drakonsqoltweaks$onDropItem(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
		HotbarRefillManager.getInstance().onPlayerDrop((LocalPlayer) (Object) this);
	}

	@Inject(method = "closeContainer", at = @At("HEAD"))
	private void drakonsqoltweaks$onCloseContainer(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (self.containerMenu instanceof AbstractFurnaceMenu furnaceMenu) {
			FurnaceTimerManager.getInstance().onClosingMenu(Minecraft.getInstance(), furnaceMenu);
		}
	}
}
