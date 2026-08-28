package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.Scrambler;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The main event: the seed every solid block in every chunk mesh is rotated by.
 *
 * <p>Before 1.21.5 the chunk mesher calls through {@code BlockRenderDispatcher.renderBatched} rather
 * than owning the seed itself, so this is the same hook as on later versions but one frame further
 * out. 1.21.5 introduced {@code SectionCompiler} and moved the call there.
 *
 * <p>Redirecting the call site rather than overriding {@code BlockStateBase.getSeed} is deliberate
 * on two counts. It is render-only by construction - vanilla's gameplay users of a position hash
 * ({@code DoorBlock}, {@code BedBlock}, {@code DoublePlantBlock}) call {@code Mth.getSeed} directly
 * and are untouched here - and it returns a primitive, where a cancellable {@code @Inject} would box
 * a {@code Long} for every block in every chunk.
 */
@Mixin(BlockRenderDispatcher.class)
public class ChunkMeshMixin {
	@Redirect(
		method = "renderBatched",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;getSeed(Lnet/minecraft/core/BlockPos;)J"))
	private long hideMyBase$scrambleSeed(BlockState state, BlockPos pos) {
		return Scrambler.seed(state, pos);
	}
}
