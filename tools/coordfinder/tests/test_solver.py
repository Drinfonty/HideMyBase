"""Solver behaviour, including the property the mod is supposed to break.

These use perfect observations generated from the forward model rather than from an image, so a
failure here is a bug in the search, never in the screenshot reader.
"""

import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from coordfinder.mcrandom import LegacyRandom, get_seed  # noqa: E402
from coordfinder.solver import (  # noqa: E402
    Observation,
    Range,
    information_bits,
    patch_from_world,
    solve,
)

X, Y, Z = 1234, 71, -5678


def scrambled_patch(x, y, z, width, depth, variants, salt):
    """What the same patch looks like with HideMyBase active.

    Mirrors the mod's salted hash closely enough for this purpose: the point is only that the
    variants no longer follow vanilla's position hash.
    """
    observations = []

    for dx in range(width):
        for dz in range(depth):
            mixed = get_seed(x + dx, y, z + dz) ^ salt
            observations.append(
                Observation(dx, 0, dz, variants, LegacyRandom(mixed).next_int(variants))
            )

    return observations


class SolverTest(unittest.TestCase):
    def test_recovers_the_exact_position_from_a_stone_patch(self):
        result = solve(
            patch_from_world(X, Y, Z, 4, 4, 4),
            Range(X - 200, X + 200),
            Range(Y, Y),
            Range(Z - 200, Z + 200),
        )

        self.assertEqual([(X, Y, Z)], result.candidates)
        self.assertTrue(result.unique)

    def test_a_single_block_is_nowhere_near_enough(self):
        # 2 bits against a 401x401 box: the tool must not present this as an identification.
        result = solve(
            patch_from_world(X, Y, Z, 1, 1, 4),
            Range(X - 200, X + 200),
            Range(Y, Y),
            Range(Z - 200, Z + 200),
            limit=100_000,
        )

        self.assertIn((X, Y, Z), result.candidates)
        self.assertGreater(len(result.candidates), 1000)
        self.assertGreater(result.expected_false_positives, 1.0)

    def test_more_blocks_collapse_the_candidate_set(self):
        # Over a 201x201 box the measured progression is 10068 -> 157 -> 2 -> 1 candidates for
        # 1x1 through 4x4. Note 3x3 still leaves a spurious hit: 18 bits against 40401 positions
        # predicts 0.15 of them, so one is ordinary luck rather than a bug. 4x4 is the first size
        # that identifies a position on its own.
        counts = []

        for size in (1, 2, 3, 4):
            result = solve(
                patch_from_world(X, Y, Z, size, size, 4),
                Range(X - 100, X + 100),
                Range(Y, Y),
                Range(Z - 100, Z + 100),
                limit=100_000,
            )
            self.assertIn((X, Y, Z), result.candidates)
            counts.append(len(result.candidates))

        self.assertEqual(counts, sorted(counts, reverse=True))
        self.assertEqual(1, counts[-1])
        self.assertGreater(counts[0], counts[-1])

    def test_netherrack_carries_twice_the_bits_of_stone(self):
        self.assertAlmostEqual(32.0, information_bits(patch_from_world(X, Y, Z, 4, 4, 4)))
        self.assertAlmostEqual(64.0, information_bits(patch_from_world(X, Y, Z, 4, 4, 16)))

    def test_y_is_recovered_too_when_scanned(self):
        result = solve(
            patch_from_world(X, Y, Z, 4, 4, 4),
            Range(X - 40, X + 40),
            Range(Y - 20, Y + 20),
            Range(Z - 40, Z + 40),
        )

        self.assertEqual([(X, Y, Z)], result.candidates)

    def test_a_scrambled_patch_defeats_the_search(self):
        # The whole point of the mod. With a salt mixed in, the vanilla hash no longer reproduces
        # what is on screen, so the true position is not among the candidates - and with 32 bits
        # against a 401x401 box, neither is anything else.
        result = solve(
            scrambled_patch(X, Y, Z, 4, 4, 4, salt=0x0123456789ABCDEF),
            Range(X - 200, X + 200),
            Range(Y, Y),
            Range(Z - 200, Z + 200),
            limit=100_000,
        )

        self.assertNotIn((X, Y, Z), result.candidates)
        self.assertEqual([], result.candidates)

    def test_corrupting_one_reading_loses_the_position(self):
        # The search demands an exact match, so a misread block is fatal rather than degrading.
        # Worth knowing: it means the screenshot reader has to be right, not merely close.
        observations = patch_from_world(X, Y, Z, 4, 4, 4)
        first = observations[0]
        observations[0] = Observation(
            first.dx, first.dy, first.dz, first.variants, (first.index + 1) % first.variants
        )

        result = solve(
            observations, Range(X - 200, X + 200), Range(Y, Y), Range(Z - 200, Z + 200)
        )

        self.assertNotIn((X, Y, Z), result.candidates)

    def test_rejects_impossible_observations(self):
        with self.assertRaises(ValueError):
            Observation(0, 0, 0, 4, 4)
        with self.assertRaises(ValueError):
            Observation(0, 0, 0, 1, 0)


if __name__ == "__main__":
    unittest.main()
