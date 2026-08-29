package com.drinfonty.hidemybase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Pins the vanilla position-to-variant maths that {@code tools/coordfinder} reimplements in Python.
 *
 * <p>The attack tool is only meaningful if it agrees with the game bit for bit, and the two live in
 * different languages, so neither can check the other directly. Instead both are pinned to one
 * checked-in fixture: this test regenerates it from the real {@code Mth} and {@code RandomSource}
 * and fails if Minecraft's answer ever changes, while the Python test asserts the reimplementation
 * reproduces the same file. A Mojang change to either function breaks this test rather than quietly
 * making the tool wrong.
 *
 * <p>Regenerate with {@code ./gradlew :common:test -Dhidemybase.writeFixture=true} after checking
 * that the change is real and intended.
 */
class VanillaRandomFixtureTest {
	private static final Path FIXTURE =
		Path.of("..", "tools", "coordfinder", "tests", "data", "mcrandom_truth.csv");

	/** Sample count and RNG seed are fixed so the fixture is byte-stable across runs. */
	private static final int SAMPLES = 4000;
	private static final long PICK_SEED = 12345L;

	private static List<String> generate() {
		Random pick = new Random(PICK_SEED);
		List<String> rows = new ArrayList<>(SAMPLES);

		for (int i = 0; i < SAMPLES; i++) {
			// Deliberately spread past +/-686 blocks: Mth.getSeed multiplies x as a 32-bit int
			// before widening, so a reimplementation that widens first only diverges out here.
			int x = pick.nextInt(4_000_000) - 2_000_000;
			int y = pick.nextInt(400) - 64;
			int z = pick.nextInt(4_000_000) - 2_000_000;

			long seed = Mth.getSeed(x, y, z);
			RandomSource random = RandomSource.createThreadLocalInstance(0L);

			// 4 and 16 are the two vanilla variant counts; 3 is not used by any block but exercises
			// nextInt's rejection path, which a power-of-two-only test would never reach.
			random.setSeed(seed);
			int v4 = random.nextInt(4);
			random.setSeed(seed);
			int v16 = random.nextInt(16);
			random.setSeed(seed);
			int v3 = random.nextInt(3);

			rows.add(x + "," + y + "," + z + "," + seed + "," + v4 + "," + v16 + "," + v3);
		}

		return rows;
	}

	@Test
	void matchesTheCheckedInFixture() {
		List<String> rows = generate();

		if (!Files.isRegularFile(FIXTURE)) {
			try {
				Files.createDirectories(FIXTURE.getParent());
				Files.write(FIXTURE, rows, StandardCharsets.UTF_8);
			} catch (IOException failed) {
				throw new UncheckedIOException(failed);
			}

			return;
		}

		List<String> expected;

		try {
			expected = Files.readAllLines(FIXTURE, StandardCharsets.UTF_8);
		} catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}

		assertEquals(expected.size(), rows.size(), "fixture row count changed");

		for (int i = 0; i < rows.size(); i++) {
			assertEquals(expected.get(i), rows.get(i), "vanilla randomness changed at row " + i);
		}
	}
}
