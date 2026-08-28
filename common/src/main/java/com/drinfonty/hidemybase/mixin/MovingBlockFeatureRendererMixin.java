package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blocks in motion - piston pushes and the like - which vanilla renders outside the chunk mesh.
 *
 * <p>Included for consistency rather than for privacy: a moving block's rotation would otherwise pop
 * as it changes hands between this renderer and the chunk mesher.
 */
@Mixin(MovingBlockFeatureRenderer.class)
public class MovingBlockFeatureRendererMixin {
	@Redirect(
		method = "buildGroup",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
