package com.drinfonty.hidemybase;

/**
 * The salted position hash that replaces {@link net.minecraft.util.Mth#getSeed}.
 *
 * <p>Vanilla derives a block's render seed from its coordinates alone, with a fixed, public
 * function. Every client in the world therefore agrees on which texture rotation each block gets -
 * and so can anyone holding a screenshot, by searching for the coordinate whose hash reproduces the
 * observed pattern. Mixing a secret into that hash is the whole mod: the mapping stays
 * deterministic, so nothing flickers and chunks still cache, but it is only reproducible by someone
 * who knows the salt.
 *
 * <p>Deliberately free of Minecraft types so the avalanche and determinism properties can be tested
 * in a plain JVM. The mixins are the only thing that needs a running game.
 */
public final class PositionHash {
	/** SplitMix64 finalizer constants - a strong 64-bit avalanche in three multiply-xorshift steps. */
	private static final long MIX_A = 0xBF58476D1CE4E5B9L;
	private static final long MIX_B = 0x94D049BB133111EBL;

	/** Odd 64-bit constants (golden-ratio derived) so each axis contributes distinctly. */
	private static final long AXIS_X = 0x9E3779B97F4A7C15L;
	private static final long AXIS_Y = 0xC2B2AE3D27D4EB4FL;
	private static final long AXIS_Z = 0x165667B19E3779F9L;

	private PositionHash() {
	}

	private static long mix(long value) {
		long z = value;
		z = (z ^ (z >>> 30)) * MIX_A;
		z = (z ^ (z >>> 27)) * MIX_B;
		return z ^ (z >>> 31);
	}

	/**
	 * The replacement for {@code Mth.getSeed(x, y, z)}: same contract (a stable long per position),
	 * different and secret function.
	 *
	 * <p>Note this is called once per block per chunk mesh, on the chunk build worker threads, so it
	 * stays branch-free and allocation-free.
	 */
	public static long seed(long salt, int x, int y, int z) {
		long h = salt;
		h = mix(h ^ (x * AXIS_X));
		h = mix(h ^ (y * AXIS_Y));
		h = mix(h ^ (z * AXIS_Z));
		return h;
	}

	/**
	 * A decoy horizontal position for the block-offset scramble.
	 *
	 * <p>Vanilla's offset functions clamp by per-block constants that are not reachable from here, so
	 * rather than reinventing the offset math we feed vanilla's own {@code getOffset} a different
	 * coordinate and let it apply its own clamps. Only X and Z are derived because vanilla's offset
	 * is seeded by {@code Mth.getSeed(x, 0, z)} - Y is not an input, and adding one would make a
	 * vertical stack of bamboo wobble instead of standing straight.
	 */
	public static long offsetDecoy(long salt, int x, int z) {
		long h = salt ^ AXIS_Y;
		h = mix(h ^ (x * AXIS_X));
		h = mix(h ^ (z * AXIS_Z));
		return h;
	}

	/** Low half of {@link #offsetDecoy}, used as the decoy X. */
	public static int decoyX(long decoy) {
		return (int) decoy;
	}

	/** High half of {@link #offsetDecoy}, used as the decoy Z. */
	public static int decoyZ(long decoy) {
		return (int) (decoy >>> 32);
	}
}
