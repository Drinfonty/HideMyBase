# coordfinder

Recovers Minecraft world coordinates from the block texture rotations visible in a screenshot.

This is the attack HideMyBase exists to defeat. It is here so the mod's claim can be tested rather
than asserted: point it at a vanilla screenshot and it prints where the shot was taken; point it at
the same scene with the mod enabled and it finds nothing.

## The attack

Vanilla picks each block's model variant from `Mth.getSeed(x, y, z)` — a fixed, public hash of the
position — by seeding `java.util.Random` with it and taking `nextInt(variantCount)`. Nothing about
that is secret, so the pattern of rotations across a patch of floor is a fingerprint of its
coordinate.

34 vanilla blocks carry randomised variants: 33 with four (stone, deepslate, sand, gravel, dirt,
bedrock, the concrete powders …) and netherrack with sixteen. That is 2 bits per block, or 4 for
netherrack. A 4×4 patch of stone is 32 bits, which is already far more than enough to pick one
position out of any realistic search box — the 7×5 patch used below carries 70.

## Result

Both screenshots are the same flat stone floor at (1234, -56, -5678), same camera, same seed. The
only difference is whether the mod was loaded.

```
════════ VANILLA CLIENT ════════
read 35 blocks (0 unreadable)
searched 59,138,441 positions in 42.4s
information: 70 bits, expected false positives 5.01e-14
RESULT: (1234, -56, -5678)

════════ HIDEMYBASE ENABLED ════════
read 35 blocks (0 unreadable)
searched 59,138,441 positions in 43.1s
RESULT: no position in the search box produces this pattern.
```

Note `0 unreadable` in both. The mod does not blur, hide or corrupt anything — the faces are read
just as cleanly with it on. What breaks is the *correspondence* between what is on screen and
vanilla's hash. The Y coordinate falls out too, because the hash takes all three axes.

## Usage

```bash
# Learn the lattice and which texture means which variant, from a shot at a known coordinate.
python3 -m coordfinder.cli calibrate shot.png --x 1234 --y -56 --z -5678 --out calibration.json

# Recover the coordinate of an unknown shot.
python3 -m coordfinder.cli attack unknown.png --calibration calibration.json \
    --near-x 1234 --near-y -56 --near-z -5678 --radius 600 --y-radius 20

# Check the forward model and solver still agree.
python3 -m coordfinder.cli selftest
```

Stdlib only, except Pillow for image reading.

## How it works, and what is load-bearing

**The RNG is reimplemented bit for bit** (`mcrandom.py`) — `Mth.getSeed` and `java.util.Random`'s
LCG, both read out of the 26.2 bytecode. The whole tool is worthless if it disagrees with the game
anywhere, so it is pinned to a fixture generated from the *real* `Mth` and `RandomSource` by
`:common`'s `VanillaRandomFixtureTest`: 4000 positions spread over ±2,000,000 blocks, checked for
bounds 4, 16 and 3. Neither side can drift without the other's test failing.

One detail that matters: `x * 3129871` is a 32-bit multiply that wraps *before* being widened to a
long. Widening first gives identical answers within ~686 blocks of spawn and wrong ones everywhere
else — which is to say, everywhere a base actually is. The fixture deliberately samples far out.

**Faces are area-averaged back to 16×16 texels** before comparison (`calibration.py`). A block is
16 texels wide but seldom an integer number of pixels — 3.75 px/texel in the sample shots — so two
faces showing the *same* variant land on different sub-texel phases and differ pixel for pixel.
Averaging each face back onto a 16×16 grid undoes that, and identical variants collapse from ten
apparent classes to exactly four. This was the single fix that made the reader work.

**The lattice is fitted, not computed.** The reader is never told the FOV or the altitude; it
searches for the pitch and offset that make the faces fall into exactly the expected number of
classes, scored by the tightest within-class spread. A wrong grid slices tiles across block
boundaries and the class count explodes immediately, so a bad fit fails loudly.

**The variant mapping is learned, not derived.** Vanilla's list mixes `y` rotation with mirrored
models, and working out how that lands on a top face from the model JSON is exactly the sort of
reasoning that is quietly wrong. Instead, at a known coordinate the right answer is computable, so
the observed classes are labelled by matching against it — and the screen-to-world axis convention
is searched over all eight possibilities rather than assumed. Only one produced a consistent
bijection across all 35 cells, which is itself strong evidence the fit is right.

**The search is brute force over a bounded box.** The hash squares its input and is not worth
inverting. What makes it cheap is that the most informative observation is checked first and rejects
three quarters of candidates immediately; the per-axis hash terms are precomputed per column. That
gives ~1.4M candidates/s in pure Python — 59 million positions, including a 41-level Y scan, in
about 40 s.

`expected_false_positives` is reported alongside every result. A patch carrying B bits leaves
roughly `volume / 2**B` spurious hits; far below 1 means a lone candidate is the real position,
well above 1 means the patch is too small and a hit would be luck. The tool says so rather than
letting a coincidence read as an identification.

## Limitations

- **Oblique views are implemented but unproven.** `rectify.py` warps a flat floor to top-down via
  the camera's homography. Its projection is validated against the top-down capture — both axis
  signs and the 59.4 px block pitch match what the empirical calibration independently recovered —
  but it has not yet been run against a real oblique screenshot end to end. Until it has, only the
  top-down path is demonstrated.

  Geometry is not the obstacle; sampling is. `usable_radius()` gives the honest cutoff: a block
  needs ~16 px across its *worst* axis, and a floor at a grazing angle is foreshortened along the
  view direction by `sin(elevation) = h/d`, so the legible pixels go as `focal·h/d²`, not `focal/d`.
  From the sample capture (8.7 blocks up, fov 70, 720p) that is a radius of about 14 blocks.
  Note it depends on camera *height*, not pitch — pitch decides what is in frame, not what is
  legible. Beyond that radius Minecraft has already drawn the face from a lower mip level, so the
  distinguishing detail is absent from the source image and no warping restores it.
- **Exact match required.** One misread face and the true position drops out of the results
  entirely, rather than ranking lower. The reader has to be right, not close.
- **A bounded search box.** Cost is proportional to volume searched. In practice an attacker has a
  box — a biome, a render distance, a region file — but this is not a whole-world scan.
- **One block type per patch**, and it has to be one of the 34 that are randomised. A floor of
  planks carries no information at all.

## Capture harness

`capture/place_player.py` puts the player at an exact coordinate looking straight down, in creative
flight, by editing the save. Player state moved in 26.x: it is `players/data/<uuid>.dat` beside a
`singleplayer_uuid` in level.dat, and `Data.Player` is no longer read — writing there looks like it
worked and silently does nothing. The script handles both layouts, and requires the save to have
been opened once so the client has created the player file.
