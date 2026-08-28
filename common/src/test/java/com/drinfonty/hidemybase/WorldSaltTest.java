package com.drinfonty.hidemybase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * The per-world mode only earns its keep if a screenshot of one world genuinely says nothing about
 * another, which comes down to: different world keys, unrelated salts.
 */
class WorldSaltTest {
	private static final byte[] SECRET = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

	@Test
	void isStableForOneWorld() {
		assertEquals(WorldSalt.derive(SECRET, "sp:My Base"), WorldSalt.derive(SECRET, "sp:My Base"));
	}

	@Test
	void separatesWorlds() {
		assertNotEquals(WorldSalt.derive(SECRET, "sp:My Base"), WorldSalt.derive(SECRET, "sp:My Other Base"));
		assertNotEquals(WorldSalt.derive(SECRET, "sp:hub"), WorldSalt.derive(SECRET, "mp:hub"));
	}

	@Test
	void separatesInstalls() {
		byte[] other = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);

		assertNotEquals(WorldSalt.derive(SECRET, "sp:My Base"), WorldSalt.derive(other, "sp:My Base"));
	}

	@Test
	void neverReturnsZero() {
		// Zero is the "not armed" sentinel in Scrambler, so it must never come out of derivation.
		for (int i = 0; i < 2000; i++) {
			assertNotEquals(0L, WorldSalt.derive(SECRET, "world-" + i));
		}
	}
}
