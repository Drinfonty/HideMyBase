package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The block-breaking crack overlay, which re-tessellates the block it is drawn over.
 *
 * <p>Easy to overlook and visible the moment it is wrong: the overlay has to pick the same model
 * variant and the same offset as the chunk mesh underneath it, or the cracks sit on a differently
 * rotated face than the block you are mining.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Redirect(
		method = "submitBlockDestroyAnimation",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}

	@Redirect(
		method = "submitBlockDestroyAnimation",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getOffset(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 hideMyBase$scrambleOffset(BlockState state, BlockPos pos) {
		return Scrambler.offset(state, pos);
	}
}
