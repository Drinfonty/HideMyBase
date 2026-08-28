package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The block-breaking crack overlay, which re-tessellates the block it is drawn over.
 *
 * <p>Easy to overlook and visible the moment it is wrong: the overlay has to pick the same model
 * variant as the chunk mesh underneath it, or the cracks sit on a differently rotated face than the
 * block you are mining.
 *
 * <p>The 1.21 line keeps this in {@code BlockRenderDispatcher}; 26.1 moves it to
 * {@code LevelRenderer.submitBlockDestroyAnimation}.
 */
@Mixin(BlockRenderDispatcher.class)
public class BreakOverlayMixin {
	@Redirect(
		method = "renderBreakingTexture",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
