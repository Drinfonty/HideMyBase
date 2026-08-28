package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blocks rendered outside the chunk mesh - piston pushes and falling blocks.
 *
 * <p>Included for consistency rather than for privacy: a moving block's rotation would otherwise pop
 * as it changes hands between this renderer and the chunk mesher.
 *
 * <p>1.21.9 merged the old {@code PistonHeadRenderer} and {@code FallingBlockRenderer} paths into
 * this one class, which is why the pre-1.21.9 branches need a second mixin here and this one does
 * not.
 */
@Mixin(BlockFeatureRenderer.class)
public class MovingBlockMixin {
	@Redirect(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
