"""Pins the Python reimplementation against ground truth taken from the real Minecraft classes.

The fixture is produced by :common's VanillaRandomFixtureTest from Mth.getSeed and RandomSource
itself, so this asserts agreement with the game rather than with another copy of my own reasoning.
Every attack result the tool produces rests on this passing.

Stdlib unittest rather than pytest on purpose: the tool has no third-party dependencies beyond
Pillow for the screenshot reader, and a test runner should not be the thing that adds one.
"""

import csv
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from coordfinder.mcrandom import LegacyRandom, get_seed  # noqa: E402

FIXTURE = pathlib.Path(__file__).parent / "data" / "mcrandom_truth.csv"


def load_rows():
    with FIXTURE.open() as handle:
        return [[int(field) for field in row] for row in csv.reader(handle)]


class VanillaAgreementTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = load_rows()

    def test_fixture_is_present_and_substantial(self):
        self.assertEqual(4000, len(self.rows))

    def test_get_seed_matches_vanilla(self):
        for x, y, z, seed, _v4, _v16, _v3 in self.rows:
            self.assertEqual(seed, get_seed(x, y, z), f"getSeed disagreed at ({x},{y},{z})")

    def test_next_int_matches_vanilla_for_every_bound(self):
        # 4 and 16 are the vanilla variant counts; 3 exercises nextInt's rejection path, which the
        # power-of-two fast path would otherwise hide.
        for x, y, z, seed, v4, v16, v3 in self.rows:
            for bound, expected in ((4, v4), (16, v16), (3, v3)):
                with self.subTest(pos=(x, y, z), bound=bound):
                    self.assertEqual(expected, LegacyRandom(seed).next_int(bound))

    def test_large_coordinates_are_covered(self):
        # x * 3129871 overflows int past ~686 blocks, and a reimplementation that widens before
        # multiplying only diverges out there. A fixture that never left spawn would pass while
        # the tool was wrong everywhere that matters.
        self.assertTrue(any(abs(row[0]) > 100_000 for row in self.rows))


if __name__ == "__main__":
    unittest.main()
