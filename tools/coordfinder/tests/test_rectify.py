"""Geometry checks for the oblique-view rectifier.

Pinned against the top-down capture the reader was calibrated on, because that shot has an
independently established ground truth: the empirical calibration recovered a 59.4 px block pitch
and screen-to-world signs of x=-1, z=-1, and the solver then recovered the true coordinate with it.
Any projection that disagrees with those is wrong, whatever the derivation looked like.
"""

import math
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from coordfinder.rectify import (  # noqa: E402
    Camera,
    focal_from_observed_pitch,
    project,
    usable_radius,
)

# The capture: straight down, yaw 0, fov 70, 1280x720, floor top at y=-55, 8.655 blocks below.
HEIGHT_ABOVE = 8.655
PLANE_Y = -55.0
CAMERA = Camera(
    x=1234.5, y=PLANE_Y + HEIGHT_ABOVE, z=-5677.5, yaw=0.0, pitch=90.0, fov=70.0,
    width=1280, height=720,
)


class ProjectionTest(unittest.TestCase):
    def test_point_below_the_camera_lands_at_the_centre(self):
        sx, sy, _ = project(CAMERA, CAMERA.x, PLANE_Y, CAMERA.z)
        self.assertAlmostEqual(640.0, sx, places=3)
        self.assertAlmostEqual(360.0, sy, places=3)

    def test_axis_signs_match_the_empirical_calibration(self):
        centre_x, centre_y, _ = project(CAMERA, CAMERA.x, PLANE_Y, CAMERA.z)
        east_x, _, _ = project(CAMERA, CAMERA.x + 1, PLANE_Y, CAMERA.z)
        _, south_y, _ = project(CAMERA, CAMERA.x, PLANE_Y, CAMERA.z + 1)

        # Calibration found x_sign=-1 and z_sign=-1: moving right or down the screen moves -X, -Z.
        self.assertLess(east_x, centre_x, "+X must move left on screen")
        self.assertLess(south_y, centre_y, "+Z must move up on screen")

    def test_block_pitch_matches_the_measured_lattice(self):
        centre_x, _, _ = project(CAMERA, CAMERA.x, PLANE_Y, CAMERA.z)
        plus_x, _, _ = project(CAMERA, CAMERA.x + 1, PLANE_Y, CAMERA.z)
        self.assertAlmostEqual(59.4, abs(plus_x - centre_x), delta=0.2)

    def test_focal_length_from_fov_and_from_measurement_agree(self):
        self.assertAlmostEqual(
            CAMERA.focal_px, focal_from_observed_pitch(59.4, HEIGHT_ABOVE), delta=0.3
        )

    def test_behind_the_camera_is_rejected(self):
        level = Camera(0, 0, 0, yaw=0.0, pitch=0.0, fov=70.0, width=1280, height=720)
        self.assertIsNone(project(level, 0, 0, -10))

    def test_usable_radius_depends_on_height_not_pitch(self):
        radii = {
            usable_radius(Camera(0, HEIGHT_ABOVE, 0, 0.0, p, 70.0, 1280, 720), 0.0)
            for p in (20.0, 45.0, 90.0)
        }
        self.assertEqual(1, len(radii), "foreshortening is a function of geometry, not aim")

    def test_usable_radius_shrinks_as_the_camera_rises(self):
        low = usable_radius(Camera(0, 6.0, 0, 0.0, 90.0, 70.0, 1280, 720), 0.0)
        high = usable_radius(Camera(0, 40.0, 0, 0.0, 90.0, 70.0, 1280, 720), 0.0)
        self.assertGreater(low, high)

    def test_a_block_at_the_usable_radius_still_spans_the_threshold(self):
        radius = usable_radius(CAMERA, PLANE_Y, min_pixels_per_block=16.0)
        depth = math.hypot(radius, HEIGHT_ABOVE)
        squashed = CAMERA.focal_px * HEIGHT_ABOVE / (depth * depth)
        self.assertAlmostEqual(16.0, squashed, delta=0.1)


if __name__ == "__main__":
    unittest.main()
