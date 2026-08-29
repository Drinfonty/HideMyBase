package com.drinfonty.hidemybase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/**
 * The session secret has to hold two properties that pull against each other: fresh every launch,
 * and rock stable within one.
 *
 * <p>The freshness half cannot be asserted from inside a single JVM - the secret is a static, so
 * there is only ever one of it here - and forking a second JVM to prove randomness would be testing
 * {@link java.security.SecureRandom} rather than this class. What is worth pinning is the stability
 * half, because getting it wrong is silent and ugly: a secret that changed mid-session would leave
 * chunks meshed before the change disagreeing with chunks meshed after, and the world would visibly
 * reshuffle as it loaded.
 */
class SessionSecretTest {
	@Test
	void isSixteenBytes() {
		assertEquals(16, SessionSecret.get().length);
	}

	@Test
	void isStableForTheLifeOfTheGame() {
		assertArrayEquals(SessionSecret.get(), SessionSecret.get());
	}

	@Test
	void handsOutCopiesRatherThanTheSecretItself() {
		byte[] first = SessionSecret.get();
		assertNotSame(first, SessionSecret.get());

		// A caller that scribbles on what it was given must not be able to change the scramble for
		// everyone else - this is the one piece of state the whole mod rests on.
		java.util.Arrays.fill(first, (byte) 0);
		assertFalse(java.util.Arrays.equals(first, SessionSecret.get()));
	}

	@Test
	void isNotTrivial() {
		byte[] secret = SessionSecret.get();
		byte[] zeros = new byte[secret.length];
		assertFalse(java.util.Arrays.equals(zeros, secret));
	}
}
