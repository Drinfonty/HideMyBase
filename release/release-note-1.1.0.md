# Release Notes - Version 1.1.0

HideMyBase stops people working out where your base is from a screenshot.

## What's new in 1.1.0

- **A settings screen.** No more editing a JSON file you would never have found. On **Fabric** it
  is the config button beside HideMyBase in ModMenu; on **NeoForge** it is the Config button in the
  mods list.
- **The secret is no longer stored.** A fresh one is generated every time you start the game and is
  never written to disk. Nothing is kept, so there is nothing for anyone to find — and nothing for
  you to manage.
- **An icon**, so the mod is recognisable in the list.

If you ran 1.0.0, your old stored secret is removed from `config/hidemybase.json` automatically the
first time you launch 1.1.0. One consequence of the change: because the secret is new each launch,
block textures are rotated differently from session to session. Nobody builds with those rotations —
the game picks them for you — but a stone wall may look subtly different next time you play.

## The problem it solves

Minecraft decides which way round to draw each block's texture from a hash of that block's
coordinates. Stone, deepslate, sand, gravel, dirt, bedrock, netherrack and the concrete powders all
get rotated or mirrored this way, and the rule is fixed and public.

That means the pattern of rotations in any picture of your floor is a fingerprint of where you are
standing. Given a screenshot, anyone can search for the coordinate whose hash reproduces the pattern
they can see. It does not take much: a 4x4 patch of stone is enough to pin down a single position,
and the Y coordinate falls out along with X and Z.

So if you stream, post screenshots, or send a picture to someone on your server, you may be handing
over your base coordinates without knowing it. No debug screen, no coordinates on display, no
landmarks needed.

## What the mod does

HideMyBase mixes a secret into that hash. Rotations stay stable while you play — nothing flickers,
nothing shimmers as you walk, and chunks still cache normally — but the pattern no longer matches
the public formula. Someone with your screenshot has nothing to search against.

**There is nothing to configure.** Install it and it works. Each world and each server also gets its
own scramble, so a screenshot of one base tells nobody anything about another.

HideMyBase is **client-side only**. It changes nothing any server can see, so it works on vanilla
servers and anywhere else, and nobody else needs to install it. It needs no other mods.

## Worth knowing

This protects pictures **you** publish. It cannot help with everything, and it is better to know
where the edges are:

- **It does not hide anything else in the shot.** Terrain shape, biome, structures, the sky, the
  moon phase and the debug screen all still give you away. Turn off F3 before you post.
- **Another player standing in your base sees vanilla rotations.** The scramble happens in your
  game, on your screen. It protects your screenshots, not theirs.
- **It does not protect screenshots you already posted.** Each session uses a new secret, so a
  picture keeps whatever scramble it was taken with.
- **Anyone can still find your base the old-fashioned way** — by following you, by looking at a map,
  or by exploring.

## Options

All in the settings screen, and all applied immediately — there is no save button.

- **Protection** — turn the whole thing off without uninstalling.
- **Scramble block rotations** — the main feature. On by default.
- **Separate secret per world** — a different scramble per world and per server. On by default.
- **Scramble plant positions** — **off by default**, and deliberately so. Grass, flowers, bamboo and
  dripstone are also nudged sideways by a position-derived amount, which is a second, smaller leak.
  Scrambling it closes that, but Minecraft uses the same value for the collision boxes of bamboo,
  dripstone and speleothems, and only your client is scrambled — so those three will look very
  slightly offset from where you actually bump into them. Grass and flowers have no collision and
  are unaffected. Turn it on if you want the extra coverage and can live with that.

Settings are stored in `config/hidemybase.json` if you would rather edit them by hand.

## Does it actually work?

The attack was built before the defence, so the answer is not a guess. The same flat stone floor was
photographed twice, with the same camera in the same world, changing only whether the mod was
loaded. A tool then read the block rotations out of each picture and searched millions of positions:

- **Without the mod** — the exact coordinate, including height, recovered from the screenshot alone.
- **With the mod** — no match anywhere in the search.

Both pictures read equally cleanly. The mod does not blur or hide anything; it just makes the answer
wrong.

## Platform Compatibility

Minecraft changed how it draws blocks several times across these versions, so one download genuinely
cannot cover them all. Pick the one matching your Minecraft version — installing the wrong one will
fail to load rather than misbehave quietly.

| Download | Minecraft |
| :--- | :--- |
| `1.1.0-mc26.2.x`  | 26.2 |
| `1.1.0-mc26.1.x`  | 26.1 - 26.1.2 |
| `1.1.0-mc1.21.11` | 1.21.9 - 1.21.11 |
| `1.1.0-mc1.21.8`  | 1.21.5 - 1.21.8 |
| `1.1.0-mc1.21.4`  | 1.21.2 - 1.21.4 |
| `1.1.0-mc1.21.1`  | 1.21 - 1.21.1 |

Each is available for both **Fabric** and **NeoForge**. Every build was launched into a real world of
its own Minecraft version to confirm it loads and takes effect.

On Fabric the settings screen needs **ModMenu**, because Fabric has no mod list of its own. Without
it the mod still works exactly the same; there is just nowhere to put the screen, and you can edit
the config file instead. NeoForge needs nothing extra.
