package com.drakonsqoltweaks.client.mixin;

import com.drakonsqoltweaks.client.config.DrakonsQolConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntRenderer.class)
public abstract class TntRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V",
		at = @At("TAIL")
	)
	private void drakonsqoltweaks$attachTntFuseTimer(
		PrimedTnt entity,
		TntRenderState state,
		float partialTick,
		CallbackInfo ci
	) {
		DrakonsQolConfig config = DrakonsQolConfig.getInstance();
		if (!config.isTntTimerEnabled()) {
			return;
		}

		float remainingTicks = Math.max(0.0F, (float) entity.getFuse() - partialTick);
		String timeText = config.getTntTimerFormat().formatTicks(remainingTicks);

		ChatFormatting color = ChatFormatting.WHITE;
		if (config.isTntDynamicColor()) {
			float seconds = remainingTicks / 20.0F;
			if (seconds > 2.5F) {
				color = ChatFormatting.GREEN;
			} else if (seconds > 1.5F) {
				color = ChatFormatting.YELLOW;
			} else if (seconds > 0.75F) {
				color = ChatFormatting.GOLD;
			} else {
				color = ChatFormatting.RED;
			}
		}

		state.nameTag = Component.literal(timeText).withStyle(color);
		if (state.nameTagAttachment == null) {
			Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
			state.nameTagAttachment = attachment != null
				? attachment
				: new Vec3(0.0, entity.getBbHeight() + 0.25, 0.0);
		}
	}
}
