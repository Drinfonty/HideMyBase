#!/usr/bin/env python3
"""Puts the player at an exact position looking straight down, by editing a save's player data.

The screenshot reader needs an axis-aligned grid of block tops, which means a known camera: a
fixed coordinate, pitch 90, and creative flight so nothing drifts. Doing that by hand is neither
repeatable nor precise.

Where the player lives moved in 26.x: it is now ``players/data/<uuid>.dat`` beside a
``singleplayer_uuid`` in level.dat, and ``Data.Player`` in level.dat is no longer read at all -
writing there looks like it worked and silently does nothing. Older saves are still supported.

The save must already have been opened once so the client has created its player file; this edits
it rather than synthesising one, so every field the version expects is present.

Usage:
    place_player.py <save dir> --x 1234.5 --y -39 --z -5677.5 [--yaw 0]
"""

from __future__ import annotations

import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from coordfinder import nbt  # noqa: E402


def _set_camera(player: dict, x: float, y: float, z: float, yaw: float) -> None:
    player["Pos"] = nbt.Tag(nbt.LIST, (nbt.DOUBLE, [x, y, z]))
    player["Motion"] = nbt.Tag(nbt.LIST, (nbt.DOUBLE, [0.0, 0.0, 0.0]))
    # pitch 90 is straight down. yaw only rotates the image; the reader recovers the mapping from
    # screen axes to world axes during calibration rather than trusting a convention.
    player["Rotation"] = nbt.Tag(nbt.LIST, (nbt.FLOAT, [yaw, 90.0]))
    player["playerGameType"] = nbt.Tag(nbt.INT, 1)
    player["OnGround"] = nbt.Tag(nbt.BYTE, 0)

    abilities = player.get("abilities")

    if abilities is not None:
        abilities.value["flying"] = nbt.Tag(nbt.BYTE, 1)
        abilities.value["mayfly"] = nbt.Tag(nbt.BYTE, 1)
        abilities.value["invulnerable"] = nbt.Tag(nbt.BYTE, 1)


def _clean_datapacks(level: pathlib.Path) -> None:
    root_name, root = nbt.load(level)
    packs = root.value["Data"].value.get("DataPacks")

    if packs is None:
        return

    packs.value["Enabled"] = nbt.Tag(nbt.LIST, (nbt.STRING, ["vanilla"]))
    packs.value["Disabled"] = nbt.Tag(nbt.LIST, (nbt.STRING, []))
    nbt.save(level, root_name, root)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("save", type=pathlib.Path, help="the save directory, not level.dat")
    parser.add_argument("--x", type=float, required=True)
    parser.add_argument("--y", type=float, required=True)
    parser.add_argument("--z", type=float, required=True)
    parser.add_argument("--yaw", type=float, default=0.0)
    args = parser.parse_args()

    _clean_datapacks(args.save / "level.dat")

    files = sorted((args.save / "players" / "data").glob("*.dat"))

    if files:
        for path in files:
            root_name, root = nbt.load(path)
            _set_camera(root.value, args.x, args.y, args.z, args.yaw)
            nbt.save(path, root_name, root)

        print(f"placed player in {len(files)} file(s) under players/data")
    else:
        # Pre-26.x layout. Also the branch taken for a world straight out of a dedicated server,
        # which has no player files at all - such a save must be opened once first.
        level = args.save / "level.dat"
        root_name, root = nbt.load(level)
        data = root.value["Data"].value

        if "Player" not in data:
            print(
                "no players/data/*.dat and no Data.Player: open the save in the client once so it\n"
                "creates the player file, then run this again",
                file=sys.stderr,
            )
            return 1

        _set_camera(data["Player"].value, args.x, args.y, args.z, args.yaw)
        nbt.save(level, root_name, root)
        print("placed player in level.dat Data.Player (pre-26.x layout)")

    print(f"  ({args.x}, {args.y}, {args.z}) yaw={args.yaw} pitch=90, creative, flying")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
