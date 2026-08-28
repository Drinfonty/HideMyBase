package com.drinfonty.hidemybase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The live scramble state the render mixins consult, and the only mutable state in the mod.
 *
 * <p>Read from the chunk build worker threads on every block, written once per world join from the
 * client thread, so the fields are volatile and nothing else synchronises. A torn read is not
 * possible for a {@code long} declared volatile, and the worst a race could do is mesh one chunk
 * with the previous world's salt - which {@link #enter} handles by being called before any chunk of
 * the new world exists.
 *
 * <p>Both toggles fail open: when inactive, every method returns exactly what vanilla would.
 */
public final class Scrambler {
	private static volatile boolean rotationActive;
	private static volatile boolean offsetActive;
	private static volatile long salt;

	private Scrambler() {
	}

	/**
	 * Arm the scramble for a world. {@code salt} is expected to already combine the install secret
	 * with the world identity - see {@code WorldSalt}.
	 */
	public static void enter(long worldSalt, boolean scrambleRotation, boolean scrambleOffset) {
		salt = worldSalt;
		rotationActive = scrambleRotation;
		offsetActive = scrambleOffset;
	}

	/** Disarm. Leaving a world returns rendering to vanilla until the next {@link #enter}. */
	public static void leave() {
		rotationActive = false;
		offsetActive = false;
		salt = 0L;
	}

	public static boolean isRotationActive() {
		return rotationActive;
	}

	public static boolean isOffsetActive() {
		return offsetActive;
	}

	public static long salt() {
		return salt;
	}

	/**
	 * Redirect target for the three vanilla {@code BlockState.getSeed} render call sites.
	 *
	 * <p>This is the feature. The returned long drives {@code WeightedVariants.collectParts}, which
	 * is what picks between the y=0/90/180/270 entries a blockstate JSON lists for stone, sand,
	 * netherrack and friends. Replacing it replaces the visible rotation pattern.
	 */
	public static long seed(BlockState state, BlockPos pos) {
		if (!rotationActive) {
			return state.getSeed(pos);
		}

		return PositionHash.seed(salt, pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Redirect target for the two vanilla {@code BlockState.getOffset} render call sites.
	 *
	 * <p>Unlike the seed, {@code getOffset} is also consulted by {@code BambooStalkBlock},
	 * {@code PointedDripstoneBlock} and {@code SpeleothemBlock} when they build collision shapes, and
	 * those run against the server's unscrambled value. Scrambling only here - at the two render call
	 * sites, never in {@code BlockStateBase.getOffset} itself - keeps the desync purely visual and
	 * confined to those three blocks, which is why the toggle ships off by default.
	 *
	 * <p>The offset math itself is vanilla's; only the coordinate handed to it is a decoy, so
	 * per-block clamps still apply and offsets stay in their normal range.
	 */
	public static Vec3 offset(BlockState state, BlockPos pos) {
		if (!offsetActive || !state.hasOffsetFunction()) {
			return state.getOffset(pos);
		}

		long decoy = PositionHash.offsetDecoy(salt, pos.getX(), pos.getZ());

		return state.getOffset(new BlockPos(PositionHash.decoyX(decoy), pos.getY(), PositionHash.decoyZ(decoy)));
	}
}
