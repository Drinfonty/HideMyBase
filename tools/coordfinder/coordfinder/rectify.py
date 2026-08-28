"""Warps an oblique screenshot of a flat floor into the top-down view the reader expects.

The ground is a plane, so the world-to-image map is a homography and an oblique shot carries the
same information a top-down one does - up to a point. Geometry is not the limit; sampling is:

* Minecraft mipmaps. Distant faces are drawn from a lower-resolution mip level, so the detail that
  separates one rotation from another is *not in the image* and no amount of warping recovers it.
* A block needs roughly 16 px across to read its 16x16 texture at all. Past that range the
  rectified output is interpolated noise that will classify confidently and wrongly.
* Grazing angles compress the depth axis, so a face can be close and still under-sampled.

So this widens the usable region rather than removing the constraint, and
:func:`usable_radius` is the honest part of the module - it says where to stop. Note it depends on
the camera's *height*, not its pitch: how foreshortened a ground point is depends on where it sits
relative to the camera, not on where the camera happens to be aimed. Pitch decides what is in
frame, not what is legible.

Status: the projection is validated against the top-down capture - both axis signs and the block
pitch match what the empirical calibration independently recovered - but it has not yet been run
against a real oblique screenshot end to end. Treat the oblique path as unproven until it has.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from PIL import Image


@dataclass(frozen=True)
class Camera:
    """A Minecraft camera. Angles in degrees, following the game's own conventions.

    ``yaw`` 0 faces +Z and increases toward -X; ``pitch`` is positive downward. ``fov`` is the
    vertical field of view, which is what the in-game slider sets.
    """

    x: float
    y: float
    z: float
    yaw: float
    pitch: float
    fov: float
    width: int
    height: int

    @property
    def focal_px(self) -> float:
        return (self.height / 2) / math.tan(math.radians(self.fov) / 2)


def _basis(camera: Camera):
    """Camera axes in world space: forward, right, up."""
    yaw = math.radians(camera.yaw)
    pitch = math.radians(camera.pitch)

    # Minecraft: yaw 0 looks along +Z, and +yaw swings toward -X.
    forward = (
        -math.sin(yaw) * math.cos(pitch),
        -math.sin(pitch),
        math.cos(yaw) * math.cos(pitch),
    )
    right = (-math.cos(yaw), 0.0, -math.sin(yaw))
    # up = right x forward rather than a written-out formula. Deriving it by hand got the Z sign
    # backwards, which the top-down capture caught: looking straight down at yaw 0 the player faces
    # +Z, so +Z must be screen-*up*, and the hand-rolled version put it screen-down.
    up = (
        right[1] * forward[2] - right[2] * forward[1],
        right[2] * forward[0] - right[0] * forward[2],
        right[0] * forward[1] - right[1] * forward[0],
    )
    return forward, right, up


def project(camera: Camera, wx: float, wy: float, wz: float):
    """World point to pixel, or None if it is behind the camera."""
    forward, right, up = _basis(camera)
    dx, dy, dz = wx - camera.x, wy - camera.y, wz - camera.z

    depth = dx * forward[0] + dy * forward[1] + dz * forward[2]

    if depth <= 1e-6:
        return None

    horizontal = dx * right[0] + dy * right[1] + dz * right[2]
    vertical = dx * up[0] + dy * up[1] + dz * up[2]
    focal = camera.focal_px

    return (
        camera.width / 2 + focal * horizontal / depth,
        camera.height / 2 - focal * vertical / depth,
        depth,
    )


def usable_radius(camera: Camera, plane_y: float, min_pixels_per_block: float = 16.0) -> float:
    """How far out a ground block still spans ``min_pixels_per_block`` along its *worst* axis.

    Distance alone is too optimistic. A floor seen at a grazing angle is foreshortened along the
    view direction, so a block can be close and still be four pixels deep on screen. The squashed
    axis scales by sin(elevation) = h/d, giving focal*h/d**2 pixels rather than focal/d, and it is
    that axis which decides whether a rotation can be read.

    Beyond this radius the texture is not merely small: Minecraft has already drawn it from a lower
    mip level, so the distinguishing detail is absent from the source image and no rectification
    brings it back. Anything the reader reports out there is invention.
    """
    height_above = camera.y - plane_y

    if height_above <= 0:
        return 0.0

    # focal * h / d**2 >= min_pixels  =>  d <= sqrt(focal * h / min_pixels)
    max_depth_squared = camera.focal_px * height_above / min_pixels_per_block
    horizontal = max_depth_squared - height_above * height_above
    return math.sqrt(horizontal) if horizontal > 0 else 0.0


def focal_from_observed_pitch(pitch_px: float, height_above: float) -> float:
    """Recover focal length from a measured top-down block pitch and a known camera height.

    The FOV itself turned out to be trustworthy: (height/2)/tan(fov/2) gives 514.1 px at fov 70,
    and fitting this against the real capture's 59.4 px pitch returns 514.1 as well. What is *not*
    trustworthy is the camera height - the eye sat 2.66 blocks above the feet position written to
    the save, not the 1.62 the player model suggests, and using the wrong height is what makes a
    correct FOV look wrong. Fit the height (or this focal length) against a known shot rather than
    assuming an eye offset.
    """
    return pitch_px * height_above


def rectify(
    image: Image.Image,
    camera: Camera,
    plane_y: float,
    centre_x: float,
    centre_z: float,
    blocks: int,
    pixels_per_block: int = 64,
) -> Image.Image:
    """Resample the ground plane into a top-down image, ``pixels_per_block`` px per block.

    Inverse-mapped and point-sampled: the source is nearest-neighbour magnified pixel art, and
    smoothing it would blend neighbouring texels and defeat the classifier. ``pixels_per_block`` is
    a multiple of 16 by default so each texel lands on a whole number of output pixels.
    """
    size = blocks * pixels_per_block
    out = Image.new(image.mode, (size, size))
    source = image.load()
    target = out.load()
    half = blocks / 2.0

    for py in range(size):
        wz = centre_z - half + (py + 0.5) / pixels_per_block

        for px in range(size):
            wx = centre_x - half + (px + 0.5) / pixels_per_block
            projected = project(camera, wx, plane_y, wz)

            if projected is None:
                continue

            sx, sy, _depth = projected

            if 0 <= sx < image.width and 0 <= sy < image.height:
                target[px, py] = source[int(sx), int(sy)]

    return out
