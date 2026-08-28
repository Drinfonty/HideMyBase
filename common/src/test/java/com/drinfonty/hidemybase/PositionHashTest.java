package com.drinfonty.hidemybase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The scramble has to hold two properties at once, and they pull against each other: the same block
 * must hash the same way every time (or the world flickers as chunks re-mesh) while neighbouring
 * blocks must look unrelated (or the pattern is still readable).
 */
class PositionHashTest {
	private static final long SALT = 0x0123456789ABCDEFL;

	@Test
	void isDeterministic() {
		assertEquals(PositionHash.seed(SALT, 100, 64, -300), PositionHash.seed(SALT, 100, 64, -300));
	}

	@Test
	void differsPerAxis() {
		long base = PositionHash.seed(SALT, 0, 0, 0);

		assertNotEquals(base, PositionHash.seed(SALT, 1, 0, 0));
		assertNotEquals(base, PositionHash.seed(SALT, 0, 1, 0));
		assertNotEquals(base, PositionHash.seed(SALT, 0, 0, 1));
	}

	@Test
	void differentSaltsGiveDifferentSeeds() {
		assertNotEquals(PositionHash.seed(SALT, 10, 70, 10), PositionHash.seed(SALT + 1, 10, 70, 10));
	}

	/**
	 * Vanilla's rotation variants are chosen by the low bits of the seed, so a uniform spread over
	 * the whole long is not enough - the two bits that actually pick a rotation have to be uniform
	 * too. A visibly striped result would still pass a naive collision test.
	 */
	@Test
	void lowBitsAreUniformAcrossAContiguousRegion() {
		int[] buckets = new int[4];

		for (int x = 0; x < 32; x++) {
			for (int y = 0; y < 32; y++) {
				for (int z = 0; z < 32; z++) {
					buckets[(int) (PositionHash.seed(SALT, x, y, z) & 3L)]++;
				}
			}
		}

		int total = 32 * 32 * 32;
		int expected = total / 4;

		for (int bucket : buckets) {
			// Well within binomial noise for n=32768, and nowhere near what a striped hash gives.
			assertTrue(Math.abs(bucket - expected) < expected * 0.1,
				"rotation bucket skewed: " + bucket + " vs expected " + expected);
		}
	}

	@Test
	void adjacentPositionsDoNotCollideEnMasse() {
		Set<Long> seen = new HashSet<>();

		for (int x = 0; x < 64; x++) {
			for (int z = 0; z < 64; z++) {
				seen.add(PositionHash.seed(SALT, x, 64, z));
			}
		}

		assertEquals(64 * 64, seen.size(), "expected no collisions across a 64x64 slab");
	}

	@Test
	void offsetDecoyIgnoresYAndIsStable() {
		long first = PositionHash.offsetDecoy(SALT, 12, -44);

		assertEquals(first, PositionHash.offsetDecoy(SALT, 12, -44));
		assertNotEquals(first, PositionHash.offsetDecoy(SALT, 13, -44));
		assertNotEquals(first, PositionHash.offsetDecoy(SALT, 12, -45));
	}

	@Test
	void offsetDecoyHalvesAreIndependent() {
		Set<Long> pairs = new HashSet<>();

		for (int x = 0; x < 128; x++) {
			for (int z = 0; z < 128; z++) {
				long decoy = PositionHash.offsetDecoy(SALT, x, z);
				pairs.add(((long) PositionHash.decoyX(decoy) << 32) | (PositionHash.decoyZ(decoy) & 0xFFFFFFFFL));
			}
		}

		assertEquals(128 * 128, pairs.size(), "decoy coordinates collided");
	}
}
