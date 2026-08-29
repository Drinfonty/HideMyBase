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

| Mixin | Target (on `main`) | Purpose |
|---|---|---|
| `ChunkMeshMixin` | `SectionCompiler.compile` | the chunk mesh — this is the feature |
| `BreakOverlayMixin` | `LevelRenderer.submitBlockDestroyAnimation` | crack overlay, must match the mesh under it |
| `MovingBlockMixin` | `MovingBlockFeatureRenderer.buildGroup` | piston-pushed blocks, so rotation doesn't pop |
| `BlockOffsetMixin` | `ModelBlockRenderer.tesselateBlock` | the offset scramble |
| `LevelLifecycleMixin` | `Minecraft.setLevel` | pick up the salt on world join |

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

- The chunk-mesh and block-offset targets are not loaded until a chunk is meshed, so a run that
  stops at the title screen leaves two of the mixins unverified. `-PquickPlay` is what makes them
  testable, and a world from the *same* Minecraft version is required - the game will not open a
  save written by a newer one. `./gradlew :fabric:runClient -PquickPlay=<save>` with a world
  generated by that version's vanilla server is the reliable recipe.
- A fresh `fabric/run/` has no `options.txt`, so the game opens the accessibility onboarding prompt
  and **quickplay never fires** - the run sits on a modal with nothing in the log to say so.
  `-PquickPlay` therefore writes a minimal `options.txt` first if none exists (`run/` is gitignored,
  so it cannot simply be committed).
- Test worlds copied from another mod's run directory carry that mod's datapacks in `level.dat`.
  This mod ships none and does not depend on Fabric API, so such a world stops on a missing-pack
  screen - equally silent. Use a world whose `DataPacks.Enabled` is just `vanilla`.

## Branch & Minecraft Version Mapping

| Branch | Built Against | Supported Minecraft | Java |
| :--- | :--- | :--- | :--- |
| **`main`** | `26.2` | `26.2` | 25 |
| **`legacy-26.1`** | `26.1.2` | `26.1` – `26.1.2` | 25 |
| **`legacy-1.21.9`** | `1.21.11` | `1.21.9` – `1.21.11` | 21 |
| **`legacy-1.21.5`** | `1.21.8` | `1.21.5` – `1.21.8` | 21 |
| **`legacy-1.21.2`** | `1.21.4` | `1.21.2` – `1.21.4` | 21 |
| **`legacy-1.21`** | `1.21.1` | `1.21` – `1.21.1` | 21 |

### Why so many branches

The hooks are one-line redirects, but they are `require = 1`: if a target method is not
found the game **crashes at class load** rather than quietly skipping the feature. So a
branch may only advertise versions whose render path it actually compiled against. The
render path moved four times across this range:

| Change | First version | Effect |
| :--- | :--- | :--- |
| `BlockState.getOffset` loses its `BlockGetter` parameter | `1.21.2` | offset redirect descriptor changes |
| Chunk meshing moves out of `BlockRenderDispatcher.renderBatched` into `SectionCompiler.compile` | `1.21.5` | the main hook changes class |
| Piston/falling block rendering merges into `BlockFeatureRenderer`; `Minecraft.setLevel` drops its second parameter | `1.21.9` | two mixins collapse into one, lifecycle hook changes |
| Break overlay moves from `BlockRenderDispatcher.renderBreakingTexture` to `LevelRenderer.submitBlockDestroyAnimation`; `BlockFeatureRenderer` gains `renderMovingBlockSubmits` | `26.1` | overlay and moving-block hooks change |
| `BlockFeatureRenderer` splits and moving blocks land in `MovingBlockFeatureRenderer.buildGroup`; `LevelRenderer.allChanged()` becomes `invalidateCompiledGeometry(...)` | `26.2` | moving-block hook and refresh call change |

## Branch Layout

Seven branches. Six target a Minecraft version; one holds everything that does not.

`shared` is an ancestor of every version branch, so its changes reach them by `git merge`
rather than by six cherry-picks.

### What lives where

**Edit on `shared`** — merged into every version branch:

- `README.md`, `LICENSE`, `.gitignore`
- `gradle/mod.properties` — `mod_version`, `maven_group`, the store project ids
- `release/release-note-*.md` — one note per version, shared by every branch
- `PositionHash.java`, `WorldSalt.java`, `HideMyBase.java` — no Minecraft types at all
- `ClientConfig.java`, `HideMyBaseClient.java`, `WorldKey.java` — Minecraft API that has not moved
- the Fabric and NeoForge entry points, `fabric.mod.json`, `neoforge.mods.toml`
- `common/src/test/**`
- `settings.gradle`, `gradlew`, `gradle/wrapper/**`

**Edit on the version branch** — never merged from `shared`:

| File | Why it is per-branch |
| :--- | :--- |
| `gradle.properties` | Every Minecraft, Loom, NeoForge, Java and mixin-level value |
| `build.gradle`, `common/build.gradle`, `fabric/build.gradle`, `neoforge/build.gradle` | The Loom plugin id needs a literal in `plugins {}`, so it cannot be a property |
| `common/.../mixin/**` | The render-path targets in the table above |
| `Scrambler.java` | Its `offset` signature follows `BlockState.getOffset` |
| `RenderRefresh.java` | `allChanged()` vs `invalidateCompiledGeometry(...)` |
| `hidemybase.mixins.json` | The mixin *list* differs — pre-`1.21.9` needs a separate falling-block mixin |

> As in RedFX, `shared` still contains a frozen copy of every per-branch file. They are
> deliberately never edited there: modifying them would conflict on every merge, and
> deleting them would cause a modify/delete conflict instead. If you find yourself editing
> one on `shared`, you are on the wrong branch.

Everything that varies is pushed into `gradle.properties` and templated through
`processResources`, so `fabric.mod.json`, `neoforge.mods.toml` and the mixin config are
byte-identical across branches.

### Two traps worth knowing

**The 1.21 branches publish a remapped jar.** They use `fabric-loom-remap`, and the artifact
players install is `remapJar`'s output in the *intermediary* namespace - not `jar`, which stays
named. `copyJarToRelease` takes `remapJar` for that reason. A dev `runClient` is a named-namespace
game, so it will happily load a broken jar's mixins from source and look healthy while the
published jar silently applies nothing. Both `:common` and `:fabric` set `useLegacyMixinAp = false`
so Loom rewrites the annotations in the bytecode instead of relying on a refmap that lands in the
wrong project's cache.

Verify a real artifact with the production client, never with `runClient`:

```
./gradlew :fabric:runProdClient -PquickPlay=<save> -PmixinDebug
```

It launches Fabric Loader the way the vanilla launcher does - obfuscated game jar plus
intermediary mappings, the published jar as the only mod in `run-prod/mods` - so a mapping
mistake fails there exactly as it would for a player. A healthy run logs
`Mixing <Name> into net.minecraft.class_NNN` for every mixin; the failure mode is a `@Mixin
target ... was not found` **warning** and a client that boots anyway, so check for the positive
lines rather than for the absence of errors. Every 1.21 branch has been through this.

**NeoForm needs a real JDK.** It recompiles Minecraft in its own JVM with `javac --release`, and
Gradle will hand it a JRE if one matches the version - Debian ships a JRE 21. A JRE has no
`ct.sym`, so the recompile fails with `release version N not supported` before any mod code is
touched. `neoforge/build.gradle` derives the executable from `javaToolchains.compilerFor`, which
only ever resolves something that can compile.

### Making a change

Version-agnostic (docs, the hash, config, a version bump):

```bash
git checkout shared
# ...edit, commit...
for b in main legacy-26.1 legacy-1.21.9 legacy-1.21.5 legacy-1.21.2 legacy-1.21; do
  git checkout $b && git merge shared
done
```

Version-specific: edit directly on the branch, never on `shared`.

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
