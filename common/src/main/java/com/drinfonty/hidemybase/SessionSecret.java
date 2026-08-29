package com.drinfonty.hidemybase;

import java.security.SecureRandom;

/**
 * The secret the scramble is keyed on: minted once when the game starts, never written to disk.
 *
 * <p>Storing it bought nothing and cost something. Against a single leaked screenshot the security
 * is identical either way, because the attacker has no secret in either case. The difference is
 * blast radius: a stored secret that leaks - read off disk, or shown on stream - retroactively
 * exposes every screenshot the player has ever posted, while a session secret exposes only that
 * session's.
 *
 * <p>It also removes a subtler risk. A screenshot taken at a coordinate the attacker already knows -
 * a public server spawn, say - hands them dozens of known-position observations to attack the
 * secret with, and {@link PositionHash} is a fast mixing function, not a keyed cryptographic PRF
 * with any guarantee against that. Rotating every session bounds what such an attack could ever be
 * worth.
 *
 * <p>The cost is that block texture patterns differ between launches. Nothing is built around them -
 * they are position-determined, so no one designs with them - but a stone wall will look subtly
 * different next session. That is the trade, and for a mod whose whole job is to make that pattern
 * meaningless it is the right way round.
 */
public final class SessionSecret {
	private static final int SECRET_BYTES = 16;

	/**
	 * Held for the life of the JVM. It has to stay stable while the game runs, or chunks meshed
	 * before it changed would disagree with chunks meshed after, and the world would visibly
	 * reshuffle as it loads.
	 */
	private static final byte[] SECRET = generate();

	private SessionSecret() {
	}

	private static byte[] generate() {
		byte[] bytes = new byte[SECRET_BYTES];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}

	/** A copy, so the one piece of state the whole mod rests on cannot be modified by a caller. */
	public static byte[] get() {
		return SECRET.clone();
	}
}
