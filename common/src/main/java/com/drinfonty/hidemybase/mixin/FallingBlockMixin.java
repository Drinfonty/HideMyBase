package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Falling sand, gravel and anvils, which render as entities rather than as part of the chunk mesh.
 *
 * <p>The descriptor is spelled out rather than matching on the name alone: {@code render} is
 * overloaded here, once for {@code FallingBlockEntity} and once as the bridge taking the generic
 * {@code Entity}. Only the former contains the seed call, and a bare {@code method = "render"} would
 * bind both and fail on the bridge.
 *
 * <p>1.21.5 reworks entity rendering around render states, which changes this descriptor; 1.21.9
 * removes the class from this path entirely - see {@link MovingBlockMixin}.
 */
@Mixin(FallingBlockRenderer.class)
public class FallingBlockMixin {
	@Redirect(
		method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
