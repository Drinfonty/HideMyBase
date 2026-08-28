# HideMyBase

Minecraft picks each block's texture rotation from a hash of its coordinates. The function is fixed
and public, so the pattern of rotations in a screenshot is effectively a coordinate stamp: given a
picture of your base, anyone can search for the position whose hash reproduces what they see.

HideMyBase mixes a secret into that hash. Rotations stay stable — nothing flickers, chunk caching is
untouched — but the mapping from pattern to position is only reproducible by someone holding your
salt.

**Client-side and cosmetic only.** Nothing here is observable by a server, so it works on any server
including vanilla, and nobody else needs to install it.

## Threat model

This defends against someone reading coordinates off an image you published — a stream, a screenshot,
a video. It is not a defence against anyone with access to your machine, your save, or your config
file, and it does not hide the other things a screenshot leaks: terrain shape, biome, structures,
sky and moon phase, or the debug screen.

It also cannot protect a screenshot taken by *another* player standing in your base with a vanilla
client. They see vanilla rotations, because the scramble is local to your renderer.

## What it changes

| Leak | Vanilla source | Scrambled | Default |
|---|---|---|---|
| Block texture rotation | `BlockState.getSeed(pos)` → variant choice | yes | on |
| Grass/flower/bamboo/dripstone offset | `BlockState.getOffset(pos)`, seeded by `Mth.getSeed(x, 0, z)` | yes | **off** |

### Why the offset scramble defaults off

`getSeed` has exactly three callers in 26.2 and all three are client rendering (`SectionCompiler`,
`LevelRenderer`, `MovingBlockFeatureRenderer`). Vanilla's gameplay users of a position hash —
`DoorBlock`, `BedBlock`, `DoublePlantBlock` — call `Mth.getSeed` directly and are untouched. So
scrambling the rotation has no gameplay or multiplayer consequence at all.

`getOffset` is different: `BambooStalkBlock`, `PointedDripstoneBlock` and `SpeleothemBlock` also
build **collision shapes** from it, and the server does that with the unscrambled value. The mod
therefore scrambles it only at the two render call sites, never in `BlockStateBase.getOffset` itself,
which confines the damage to a purely visual mismatch on those three blocks. Grass and flowers have
no collision and are unaffected. It is a real second coordinate leak, so the switch exists — it just
isn't free, so you opt in.

## Configuration

`config/hidemybase.json`, written on first launch:

```json
{
  "schemaVersion": 1,
  "enabled": true,
  "scrambleRotation": true,
  "scrambleOffset": false,
  "perWorldSalt": true,
  "secret": "<32 hex chars, generated once>"
}
```

`secret` is 128 bits from `SecureRandom`, generated once and never sent anywhere. Delete the field to
reroll it — every world's appearance changes.

With `perWorldSalt` on (the default), the effective salt is `SHA-256(secret || worldKey)` folded to
64 bits, where `worldKey` is the save name in singleplayer or the server address in multiplayer. A
screenshot of one world therefore reveals nothing about another.

Changing settings takes effect on the next world join. `HideMyBaseClient.reload()` forces a live
re-mesh, but there is no UI wired to it yet — see TODO.

## Design

Five mixins, all `@Redirect` at a single call site each, all client-side:

| Mixin | Target | Purpose |
|---|---|---|
| `SectionCompilerMixin` | `compile` | the chunk mesh — this is the feature |
| `LevelRendererMixin` | `submitBlockDestroyAnimation` | crack overlay, must match the mesh under it |
| `MovingBlockFeatureRendererMixin` | `buildGroup` | piston-pushed blocks, so rotation doesn't pop |
| `ModelBlockRendererMixin` | `tesselateBlock` | the offset scramble |
| `MinecraftMixin` | `setLevel` | pick up the salt on world join |

Redirects rather than an `@Inject` on `BlockStateBase.getSeed` for two reasons: they are render-only
by construction, and they return a primitive, where a cancellable `@Inject` would box a `Long` for
every block in every chunk.

`setLevel` runs before any section of the new world is queued for meshing, which is what lets
`Scrambler` hand the salt to the chunk-build worker threads through plain `volatile` fields with no
other synchronisation.

The offset scramble does not reimplement vanilla's offset math — per-block clamp constants aren't
reachable from a mixin. It feeds vanilla's own `getOffset` a decoy coordinate derived from the real
one, so clamps still apply and offsets stay in their normal range.

`PositionHash`, `WorldSalt` and `ClientConfig` contain no Minecraft types, so the properties that
matter are unit-tested in a plain JVM: determinism, per-axis sensitivity, per-world separation, and
uniformity of the low two bits — the ones that actually select a rotation, where a naive collision
test would happily pass a visibly striped hash.

## Building

```
./gradlew build                        # both jars into release/
./gradlew :common:test                 # the pure-logic tests
./gradlew :fabric:runClient            # dev client
./gradlew :fabric:runClient -PquickPlay=<save>   # boot straight into a world
./gradlew :fabric:runClient -PmixinDebug         # verbose Mixin + dump transformed classes
./gradlew :fabric:runClient -PtestJar   # run from the packaged jar only
```

### Verifying the mixins actually applied

Nothing about a successful `build` says the redirects resolved - Mixin binds them at class load.
`-PmixinDebug` logs each application and writes the post-transform bytecode to
`fabric/run/.mixin.out/`, where the redirect can be confirmed directly:

```
javap -p -c fabric/run/.mixin.out/class/net/minecraft/client/renderer/chunk/SectionCompiler.class \
  | grep -E "getSeed|Scrambler"
```

A correctly applied redirect leaves **no** call to `BlockState.getSeed` in that method - it has been
replaced by `Scrambler.seed`.

Two things about the dev run are worth knowing before spending an afternoon on them:

- `SectionCompiler` and `ModelBlockRenderer` are not loaded until a chunk is meshed, so a run that
  stops at the title screen verifies only three of the five mixins. `-PquickPlay` is what makes the
  other two testable.
- A fresh `fabric/run/` has no `options.txt`, so the game opens the accessibility onboarding prompt
  and **quickplay never fires** - the run sits on a modal with nothing in the log to say so.
  `-PquickPlay` therefore writes a minimal `options.txt` first if none exists (`run/` is gitignored,
  so it cannot simply be committed).
- Test worlds copied from another mod's run directory carry that mod's datapacks in `level.dat`.
  This mod ships none and does not depend on Fabric API, so such a world stops on a missing-pack
  screen - equally silent. Use a world whose `DataPacks.Enabled` is just `vanilla`.

## Version support

Targets 26.2. Ports to 26.1.x and 1.21.x are planned.

The hooks themselves are unusually portable — `BlockState.getSeed(BlockPos)` and
`BlockState.getOffset(BlockPos)` have been stable for many versions. What moves is the *enclosing*
method names the redirects point at, and one API:

- `SectionCompiler.compile` was `ChunkRenderDispatcher`/`RenderChunkRegion` territory in 1.21.x.
- `MovingBlockFeatureRenderer` is new in the 26.x render rework and has no 1.21.x equivalent.
- `RenderRefresh` calls `LevelRenderer.invalidateCompiledGeometry(...)`, which replaced
  `allChanged()` in 26.2. It is deliberately isolated and wrapped in a `catch (LinkageError |
  RuntimeException)` so a signature change costs a rejoin, not a crash.

## TODO

- [ ] A controlled visual A/B. Comparing screenshots across two dev runs does not work: two runs
      with identical settings differ by ~35% of pixels from camera and entity drift alone, which
      swamps the texture change entirely. Doing this properly needs a frozen camera - spectator
      mode, `doDaylightCycle false`, no entities - or a headless harness that renders one chunk and
      dumps the chosen variants instead of pixels.
- [ ] Config screen / keybind, wired to `HideMyBaseClient.reload()`
- [ ] Decide whether to scramble on a per-dimension basis as well as per-world
- [ ] 26.1.x and 1.21.x branches

## License

CC0-1.0.
