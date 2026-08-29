"""Command line for the coordinate-recovery tool."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

from PIL import Image

from .calibration import Calibration, calibrate, read_observations
from .solver import Observation, Range, patch_from_world, solve


def _box(args, centre):
    half = args.radius
    cx, cy, cz = centre
    y_lo = args.y_min if args.y_min is not None else cy - args.y_radius
    y_hi = args.y_max if args.y_max is not None else cy + args.y_radius
    return Range(cx - half, cx + half), Range(y_lo, y_hi), Range(cz - half, cz + half)


def cmd_calibrate(args) -> int:
    image = Image.open(args.image).convert("L")
    result, spread = calibrate(image, args.x, args.y, args.z, args.variants, args.cols, args.rows)
    result.save(args.out)
    print(f"lattice   : pitch={result.pitch:.2f}px origin=({result.x0:.2f},{result.y0:.2f})")
    print(f"classes   : {result.variants}, tightest within-class spread {spread:.2f}")
    print(f"axes      : screen(col,row) -> world "
          f"({'dz,dx' if result.swap_axes else 'dx,dz'}) "
          f"signs x={result.x_sign:+d} z={result.z_sign:+d}")
    print(f"class->variant: {result.class_to_variant}")
    print(f"written   : {args.out}")
    return 0


def cmd_attack(args) -> int:
    calibration = Calibration.load(args.calibration)
    image = Image.open(args.image).convert("L")
    observations, unreadable = read_observations(image, calibration)

    print(f"read {len(observations)} blocks ({unreadable} unreadable)")

    if unreadable:
        print("  warning: unreadable faces are still fed to the search, and the search demands an")
        print("  exact match, so any misread guarantees a miss. Re-check the grid or the crop.")

    x_range, y_range, z_range = _box(args, (args.near_x, args.near_y, args.near_z))
    started = time.time()
    result = solve(observations, x_range, y_range, z_range, limit=args.limit)
    elapsed = time.time() - started

    print(f"searched {result.searched:,} positions in {elapsed:.1f}s")
    print(f"information: {result.bits:.0f} bits, expected false positives "
          f"{result.expected_false_positives:.2e}")

    if not result.candidates:
        print("RESULT: no position in the search box produces this pattern.")
        print("        Either the box is wrong, or the rotations are not vanilla's - which is")
        print("        exactly what a scrambling mod looks like from here.")
        return 1

    for candidate in result.candidates[:20]:
        print(f"RESULT: {candidate}")

    if result.expected_false_positives > 0.05:
        print("  caution: the patch is small for this search box, so a hit may be coincidence.")

    return 0


def cmd_selftest(args) -> int:
    x, y, z = 1234, 71, -5678
    observations = patch_from_world(x, y, z, 4, 4, 4)
    result = solve(observations, Range(x - 200, x + 200), Range(y, y), Range(z - 200, z + 200))
    ok = result.candidates == [(x, y, z)]
    print(f"forward model + solver round trip: {'ok' if ok else 'FAILED'} -> {result.candidates}")
    return 0 if ok else 1


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="coordfinder",
        description="Recover Minecraft world coordinates from block texture rotations in a "
                    "screenshot. Built to test HideMyBase, which exists to defeat it.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    c = sub.add_parser("calibrate", help="learn the lattice and variant mapping from a known shot")
    c.add_argument("image", type=Path)
    c.add_argument("--x", type=int, required=True)
    c.add_argument("--y", type=int, required=True)
    c.add_argument("--z", type=int, required=True)
    c.add_argument("--variants", type=int, default=4)
    c.add_argument("--cols", type=int, default=7)
    c.add_argument("--rows", type=int, default=5)
    c.add_argument("--out", type=Path, default=Path("calibration.json"))
    c.set_defaults(func=cmd_calibrate)

    a = sub.add_parser("attack", help="recover the coordinate of an unknown screenshot")
    a.add_argument("image", type=Path)
    a.add_argument("--calibration", type=Path, required=True)
    a.add_argument("--near-x", type=int, required=True, help="centre of the search box")
    a.add_argument("--near-y", type=int, required=True)
    a.add_argument("--near-z", type=int, required=True)
    a.add_argument("--radius", type=int, default=600)
    a.add_argument("--y-radius", type=int, default=20)
    a.add_argument("--y-min", type=int)
    a.add_argument("--y-max", type=int)
    a.add_argument("--limit", type=int, default=50)
    a.set_defaults(func=cmd_attack)

    s = sub.add_parser("selftest", help="check the forward model and solver agree")
    s.set_defaults(func=cmd_selftest)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
