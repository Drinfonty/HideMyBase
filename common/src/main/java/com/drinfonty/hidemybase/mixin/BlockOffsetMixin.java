package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The horizontal offset applied to grass, flowers, bamboo and dripstone as they are tessellated.
 *
 * <p>This is the render half of the offset scramble; {@code BlockStateBase.getOffset} itself is left
 * alone precisely because vanilla also builds collision shapes from it. See
 * {@link Scrambler#offset} for why that split matters and what it costs.
 */
@Mixin(ModelBlockRenderer.class)
public class BlockOffsetMixin {
	@Redirect(
		method = "tesselateBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getOffset(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 hideMyBase$scrambleOffset(BlockState state, BlockPos pos) {
		return Scrambler.offset(state, pos);
	}
}
