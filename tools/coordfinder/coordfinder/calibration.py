"""Turning a screenshot into variant indices, and learning how to do so from a known coordinate.

Two things have to be pinned before a screenshot can be read:

1. **The lattice.** Where the block grid sits in the image. Recovered by fitting, not by camera
   maths, so the reader cannot be quietly wrong about FOV or altitude.
2. **Which texture means which variant.** Vanilla's variant list mixes model rotation with mirrored
   models, and how that lands on a top face is not something worth deriving from the model JSON.
   Instead the mapping is *learned*: at a known coordinate the correct answer is computable, so the
   observed classes can be labelled by matching against it.

The learned mapping is checked for consistency across every cell in the calibration patch, and the
screen-to-world axis convention is searched rather than assumed. If any of it is wrong, no
consistent labelling exists and calibration fails loudly instead of producing plausible nonsense.
"""

from __future__ import annotations

import itertools
import json
from dataclasses import asdict, dataclass
from pathlib import Path

from PIL import Image

from .mcrandom import variant_index

TEXELS = 16
MATCH_TOLERANCE = 8.0


@dataclass(frozen=True)
class Calibration:
    pitch: float
    x0: float
    y0: float
    cols: int
    rows: int
    anchor_col: int
    anchor_row: int
    swap_axes: bool
    x_sign: int
    z_sign: int
    variants: int
    centroids: list[list[int]]
    class_to_variant: dict[str, int]

    def save(self, path) -> None:
        Path(path).write_text(json.dumps(asdict(self), indent=2))

    @staticmethod
    def load(path) -> "Calibration":
        return Calibration(**json.loads(Path(path).read_text()))


def tile_signature(image: Image.Image, left: float, top: float, pitch: float) -> tuple[int, ...]:
    """One block face, area-averaged back to its 16x16 texels.

    This is the step that makes the whole thing work. A block is 16 texels wide but rarely an
    integer number of pixels, so two faces showing the *same* variant land on different sub-texel
    phases and differ pixel for pixel. Averaging each face back onto a 16x16 grid undoes that, and
    identical variants collapse to near-identical signatures.
    """
    return tuple(
        image.resize((TEXELS, TEXELS), Image.BOX, box=(left, top, left + pitch, top + pitch))
        .get_flattened_data()
    )


def _distance(a, b) -> float:
    return sum(abs(p - q) for p, q in zip(a, b)) / len(a)


def read_signatures(image: Image.Image, pitch, x0, y0, cols, rows, anchor_col, anchor_row):
    cells = []

    for row in range(-anchor_row, rows - anchor_row):
        for col in range(-anchor_col, cols - anchor_col):
            left = x0 + col * pitch
            top = y0 + row * pitch
            cells.append(((col, row), tile_signature(image, left, top, pitch)))

    return cells


def cluster(signatures, tolerance: float = MATCH_TOLERANCE):
    centroids: list[tuple[int, ...]] = []
    labels = []

    for signature in signatures:
        match = None

        for index, centroid in enumerate(centroids):
            if _distance(signature, centroid) <= tolerance:
                match = index
                break

        if match is None:
            centroids.append(signature)
            match = len(centroids) - 1

        labels.append(match)

    return labels, centroids


def fit_grid(image: Image.Image, variants: int, cols: int, rows: int, anchor_col, anchor_row,
             pitch_lo=20.0, pitch_hi=200.0):
    """Find the lattice by asking which one makes the faces fall into exactly ``variants`` classes.

    A wrong pitch or offset slices tiles across block boundaries, and the classes multiply
    immediately; the right one collapses them. Scored by the tightest within-class spread so that a
    harmonic of the true pitch, which also produces clean classes, loses to the fundamental.
    """
    centre_x, centre_y = image.width / 2, image.height / 2
    best = None
    coarse = None

    step = 0.5
    pitch = pitch_lo

    while pitch <= pitch_hi:
        for dx in (-2, -1, 0, 1, 2):
            for dy in (-2, -1, 0, 1, 2):
                x0 = centre_x - pitch / 2 + dx
                y0 = centre_y - pitch / 2 + dy

                if x0 - anchor_col * pitch < 0 or y0 - anchor_row * pitch < 0:
                    continue
                if x0 + (cols - anchor_col) * pitch > image.width:
                    continue
                if y0 + (rows - anchor_row) * pitch > image.height:
                    continue

                cells = read_signatures(image, pitch, x0, y0, cols, rows, anchor_col, anchor_row)
                labels, centroids = cluster([s for _, s in cells])

                if len(centroids) != variants:
                    continue

                spread = 0.0

                for i in range(len(cells)):
                    for j in range(i + 1, len(cells)):
                        if labels[i] == labels[j]:
                            spread = max(spread, _distance(cells[i][1], cells[j][1]))

                if coarse is None or spread < coarse[0]:
                    coarse = (spread, pitch, x0, y0)

        pitch += step

    if coarse is None:
        return None

    # Refine around the coarse winner; sub-pixel offset matters more than sub-pixel pitch here.
    _, pitch, x0, y0 = coarse

    for fine_pitch in [pitch - 0.4 + 0.05 * k for k in range(17)]:
        for dx in [-1.5 + 0.25 * k for k in range(13)]:
            for dy in [-1.5 + 0.25 * k for k in range(13)]:
                cx = x0 + dx
                cy = y0 + dy
                cells = read_signatures(image, fine_pitch, cx, cy, cols, rows, anchor_col, anchor_row)
                labels, centroids = cluster([s for _, s in cells])

                if len(centroids) != variants:
                    continue

                spread = 0.0

                for i in range(len(cells)):
                    for j in range(i + 1, len(cells)):
                        if labels[i] == labels[j]:
                            spread = max(spread, _distance(cells[i][1], cells[j][1]))

                if best is None or spread < best[0]:
                    best = (spread, fine_pitch, cx, cy)

    return best


def calibrate(image: Image.Image, x: int, y: int, z: int, variants: int, cols: int, rows: int):
    """Learn the lattice, the axis convention and the class-to-variant map from a known position."""
    anchor_col, anchor_row = cols // 2, rows // 2
    fit = fit_grid(image, variants, cols, rows, anchor_col, anchor_row)

    if fit is None:
        raise RuntimeError(
            "no lattice produced the expected number of visual classes - is the shot top-down, "
            "is the floor a single randomised block type, and is the whole patch unobstructed?"
        )

    spread, pitch, x0, y0 = fit
    cells = read_signatures(image, pitch, x0, y0, cols, rows, anchor_col, anchor_row)
    labels, centroids = cluster([s for _, s in cells])
    by_cell = {cell: label for (cell, _), label in zip(cells, labels)}

    for swap, x_sign, z_sign in itertools.product((False, True), (1, -1), (1, -1)):
        mapping: dict[int, int] = {}
        ok = True

        for (col, row), label in by_cell.items():
            dx, dz = (row * x_sign, col * z_sign) if swap else (col * x_sign, row * z_sign)
            expected = variant_index(x + dx, y, z + dz, variants)

            if mapping.get(label, expected) != expected:
                ok = False
                break
            if label not in mapping and expected in mapping.values():
                ok = False
                break

            mapping[label] = expected

        if ok and len(mapping) == variants:
            return Calibration(
                pitch=pitch,
                x0=x0,
                y0=y0,
                cols=cols,
                rows=rows,
                anchor_col=anchor_col,
                anchor_row=anchor_row,
                swap_axes=swap,
                x_sign=x_sign,
                z_sign=z_sign,
                variants=variants,
                centroids=[list(c) for c in centroids],
                class_to_variant={str(k): v for k, v in mapping.items()},
            ), spread

    raise RuntimeError(
        "the lattice fit but no screen-to-world orientation reproduced vanilla's variants - the "
        "given coordinate is probably not where this screenshot was taken"
    )


def read_observations(image: Image.Image, calibration: Calibration):
    """Classify each face against the calibration centroids and emit solver observations."""
    from .solver import Observation

    cells = read_signatures(
        image,
        calibration.pitch,
        calibration.x0,
        calibration.y0,
        calibration.cols,
        calibration.rows,
        calibration.anchor_col,
        calibration.anchor_row,
    )

    observations = []
    unreadable = 0

    for (col, row), signature in cells:
        best, best_distance = None, None

        for index, centroid in enumerate(calibration.centroids):
            distance = _distance(signature, centroid)

            if best_distance is None or distance < best_distance:
                best, best_distance = index, distance

        if best_distance > MATCH_TOLERANCE:
            unreadable += 1

        variant = calibration.class_to_variant[str(best)]

        if calibration.swap_axes:
            dx, dz = row * calibration.x_sign, col * calibration.z_sign
        else:
            dx, dz = col * calibration.x_sign, row * calibration.z_sign

        observations.append(Observation(dx, 0, dz, calibration.variants, variant))

    return observations, unreadable
