package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blocks being pushed by a piston, which vanilla renders outside the chunk mesh.
 *
 * <p>Included for consistency rather than for privacy: a moving block's rotation would otherwise pop
 * as it changes hands between this renderer and the chunk mesher.
 *
 * <p>Before 1.21.9 pistons and falling blocks are two separate renderers, so this branch needs
 * {@link FallingBlockMixin} alongside it; 1.21.9 merges both into {@code BlockFeatureRenderer}.
 */
@Mixin(PistonHeadRenderer.class)
public class MovingBlockMixin {
	@Redirect(
		method = "renderBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
