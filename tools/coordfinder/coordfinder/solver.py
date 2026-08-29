"""Recovers a world coordinate from a patch of observed block-variant indices.

The attack in one line: vanilla picks each block's model variant from a public hash of its
position, so a patch of blocks whose variants you can read is a fingerprint you can search for.

Each 4-variant block contributes 2 bits and netherrack contributes 4, so a 4x4 patch of stone is
about 32 bits - far more than enough to pick one position out of any realistic search box. The
search itself is brute force, because the hash squares its input and is not worth inverting; what
makes it cheap is that three quarters of candidates die on the first observation.
"""

from __future__ import annotations

import itertools
from dataclasses import dataclass
from typing import Iterable, Iterator, Sequence

from .mcrandom import LegacyRandom, _to_signed_int, _to_signed_long

MASK48 = (1 << 48) - 1
MULTIPLIER = 0x5DEECE66D
INCREMENT = 0xB


@dataclass(frozen=True)
class Observation:
    """One block read off the screenshot, positioned relative to the patch anchor."""

    dx: int
    dy: int
    dz: int
    variants: int
    index: int

    def __post_init__(self) -> None:
        if self.variants < 2:
            raise ValueError("a block with fewer than two variants carries no information")
        if not 0 <= self.index < self.variants:
            raise ValueError(f"variant index {self.index} outside 0..{self.variants - 1}")


@dataclass(frozen=True)
class Range:
    lo: int
    hi: int

    def __post_init__(self) -> None:
        if self.hi < self.lo:
            raise ValueError("range hi is below lo")

    def __iter__(self) -> Iterator[int]:
        return iter(range(self.lo, self.hi + 1))

    def __len__(self) -> int:
        return self.hi - self.lo + 1


@dataclass(frozen=True)
class Result:
    candidates: list[tuple[int, int, int]]
    searched: int
    bits: float
    expected_false_positives: float
    truncated: bool

    @property
    def unique(self) -> bool:
        return len(self.candidates) == 1


def information_bits(observations: Sequence[Observation]) -> float:
    """Total bits the patch carries - log2 of the product of the variant counts."""
    import math

    return sum(math.log2(observation.variants) for observation in observations)


def _variant_pow2(seed: int, bound_shift: int) -> int:
    """nextInt for a power-of-two bound, inlined.

    Both vanilla variant counts (4 and 16) are powers of two, so nextInt never takes its rejection
    path and the whole call collapses to one LCG step. This is the hot loop of the entire tool.
    """
    state = (seed ^ MULTIPLIER) & MASK48
    state = (state * MULTIPLIER + INCREMENT) & MASK48
    bits = state >> 17  # next(31), always non-negative, so no sign fixup is needed
    return (bits << bound_shift) >> 31


def _variant_any(seed: int, bound: int) -> int:
    return LegacyRandom(seed).next_int(bound)


def solve(
    observations: Sequence[Observation],
    x_range: Range,
    y_range: Range,
    z_range: Range,
    limit: int = 1000,
) -> Result:
    """Search a box for anchor positions whose vanilla variants reproduce every observation.

    The box is required rather than optional: the hash is not invertible in any useful way, so the
    cost is proportional to the volume searched, and a whole-world scan is not something pure Python
    is going to finish. In practice the attacker always has a box - a biome, a render distance, a
    region file, or in our case "the place the screenshot was taken".
    """
    if not observations:
        raise ValueError("no observations to solve from")

    # Most-informative first: a netherrack block kills 15 of 16 candidates where a 4-variant block
    # kills 3 of 4, and the ordering only matters for speed, never for the answer.
    ordered = sorted(observations, key=lambda o: -o.variants)
    plan = []

    for observation in ordered:
        shift = observation.variants.bit_length() - 1
        power_of_two = (observation.variants & (observation.variants - 1)) == 0
        plan.append((observation, shift if power_of_two else None))

    candidates: list[tuple[int, int, int]] = []
    searched = 0
    truncated = False

    x_values = list(x_range)
    z_values = list(z_range)

    # get_seed mixes the axes only by XOR, and each axis term depends on one coordinate alone, so
    # the per-axis work is hoisted out of the innermost loop entirely. Without this the tool spends
    # most of its time recomputing the same handful of multiplies for every candidate.
    #
    # The tables are keyed by the patch's distinct offsets, not by the search volume squared, so
    # they stay small: |x_range| * distinct dx, plus |z_range| * distinct dz.
    ax_tables = []
    bz_tables = []

    for observation, _shift in plan:
        ax_tables.append(
            [_to_signed_long(_to_signed_int((x + observation.dx) * 3129871)) for x in x_values]
        )
        bz_tables.append([_to_signed_long((z + observation.dz) * 116129781) for z in z_values])

    head_observation, head_shift = plan[0]
    head_ax, head_bz = ax_tables[0], bz_tables[0]
    head_index = head_observation.index
    tail = list(zip(plan[1:], ax_tables[1:], bz_tables[1:]))
    z_count = len(z_values)

    for y in y_range:
        # y enters the hash directly, so each observation's row offset is a single scalar.
        ys = [_to_signed_long(y + observation.dy) for observation, _shift in plan]
        head_y = ys[0]

        for xi in range(len(x_values)):
            head_ax_x = head_ax[xi]
            searched += z_count

            for zi in range(z_count):
                # The most-informative observation is checked alone first: it rejects at least
                # three quarters of candidates, and everything else is only reached by survivors.
                l = head_ax_x ^ head_bz[zi] ^ head_y
                l = _to_signed_long(_to_signed_long(l * l) * 42317861 + _to_signed_long(l * 11))
                seed = l >> 16

                if head_shift is not None:
                    state = (seed ^ MULTIPLIER) & MASK48
                    state = (state * MULTIPLIER + INCREMENT) & MASK48
                    got = ((state >> 17) << head_shift) >> 31
                else:
                    got = _variant_any(seed, head_observation.variants)

                if got != head_index:
                    continue

                hit = True

                for i, ((observation, shift), ax, bz) in enumerate(tail, start=1):
                    l = ax[xi] ^ bz[zi] ^ ys[i]
                    l = _to_signed_long(_to_signed_long(l * l) * 42317861 + _to_signed_long(l * 11))
                    seed = l >> 16

                    got = _variant_pow2(seed, shift) if shift is not None else _variant_any(
                        seed, observation.variants
                    )

                    if got != observation.index:
                        hit = False
                        break

                if hit:
                    candidates.append((x_values[xi], y, z_values[zi]))

                    if len(candidates) >= limit:
                        truncated = True
                        break

            if truncated:
                break
        if truncated:
            break

    bits = information_bits(observations)
    volume = len(x_range) * len(y_range) * len(z_range)

    return Result(
        candidates=candidates,
        searched=searched,
        bits=bits,
        # A patch carrying B bits leaves roughly volume / 2**B spurious hits. Far below 1 means a
        # single candidate is almost certainly the real position; well above 1 means the patch is
        # too small to identify anything and a lone hit would be luck.
        expected_false_positives=volume / (2.0**bits),
        truncated=truncated,
    )


def observe(x: int, y: int, z: int, variants: int) -> int:
    """The variant vanilla shows at an absolute position - the forward direction of the attack."""
    from .mcrandom import variant_index

    return variant_index(x, y, z, variants)


def patch_from_world(
    x: int, y: int, z: int, width: int, depth: int, variants: int
) -> list[Observation]:
    """Build the observations a perfect reader would extract from a flat patch at (x, y, z).

    Used by the tests and by ``coordfinder selftest`` to exercise the solver without a screenshot.
    """
    return [
        Observation(dx, 0, dz, variants, observe(x + dx, y, z + dz, variants))
        for dx, dz in itertools.product(range(width), range(depth))
    ]
