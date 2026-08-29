"""Bit-exact reimplementation of the two pieces of Minecraft randomness this attack needs.

Both were read out of the 26.2 client bytecode rather than from memory or a wiki, because the
whole tool is worthless if it disagrees with the game by even one bit:

  Mth.getSeed(int,int,int)                     - the position hash
  SingleThreadedRandomSource / BitRandomSource - java.util.Random's LCG, which the block
                                                 renderer seeds with that hash

``tests/test_mcrandom.py`` pins these against values produced by the real classes.
"""

MASK48 = (1 << 48) - 1
MULTIPLIER = 0x5DEECE66D
INCREMENT = 0xB

INT_MIN = -(1 << 31)
INT_MAX = (1 << 31) - 1


def _to_signed_long(value: int) -> int:
    value &= (1 << 64) - 1
    return value - (1 << 64) if value >= (1 << 63) else value


def _to_signed_int(value: int) -> int:
    value &= (1 << 32) - 1
    return value - (1 << 32) if value >= (1 << 31) else value


def get_seed(x: int, y: int, z: int) -> int:
    """``Mth.getSeed``.

    Note ``x * 3129871`` is an *int* multiply in Java and wraps at 32 bits before being widened
    to a long, while the ``z`` multiply is already long. Getting that wrong only shows up at
    coordinates past ~686 blocks, which is exactly where a real base is, so it is not a detail
    that can be left to chance.
    """
    l = _to_signed_long(_to_signed_int(x * 3129871)) ^ _to_signed_long(z * 116129781) ^ _to_signed_long(y)
    l = _to_signed_long(_to_signed_long(l * l) * 42317861 + _to_signed_long(l * 11))
    return l >> 16  # Java's >>, an arithmetic shift, not >>>.


class LegacyRandom:
    """java.util.Random's LCG, as Minecraft's SingleThreadedRandomSource implements it."""

    __slots__ = ("_seed",)

    def __init__(self, seed: int) -> None:
        self.set_seed(seed)

    def set_seed(self, seed: int) -> None:
        self._seed = (seed ^ MULTIPLIER) & MASK48

    def next(self, bits: int) -> int:
        self._seed = (self._seed * MULTIPLIER + INCREMENT) & MASK48
        return _to_signed_int(self._seed >> (48 - bits))

    def next_int(self, bound: int) -> int:
        if bound <= 0:
            raise ValueError("bound must be positive")

        if (bound & (bound - 1)) == 0:  # power of two
            return (bound * self.next(31)) >> 31

        while True:
            bits = self.next(31)
            value = bits % bound
            # Reject the tail that would bias the distribution - the same guard java.util.Random uses.
            if bits - value + (bound - 1) >= 0:
                return value


def variant_index(x: int, y: int, z: int, total_weight: int) -> int:
    """The index the block renderer picks for a position, for a list of the given total weight.

    ``ModelBlockRenderer.tesselateBlock`` seeds its RandomSource with ``BlockState.getSeed(pos)``
    and ``WeightedList.getRandomOrThrow`` then takes ``nextInt(totalWeight)``. For the vanilla
    randomised blocks every entry has weight 1, so this index *is* the variant index.
    """
    return LegacyRandom(get_seed(x, y, z)).next_int(total_weight)
