package com.drinfonty.hidemybase.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.drinfonty.hidemybase.HideMyBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * {@code config/hidemybase.json}. Client-only by nature: every setting here changes what this
 * client draws and nothing that any server can observe.
 *
 * <p>Deliberately holds no secret. The scramble key is minted per session by
 * {@link com.drinfonty.hidemybase.SessionSecret} and never written anywhere, so this file contains
 * nothing worth stealing. Schema 2 exists precisely to strip the stored secret that schema 1 wrote:
 * the version bump forces a rewrite, and since the field no longer exists on this class it is
 * dropped on the way out.
 */
public final class ClientConfig {
	public static final int SCHEMA_VERSION = 2;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int schemaVersion = SCHEMA_VERSION;

	/** Master switch. Off restores stock rendering everywhere without uninstalling. */
	public boolean enabled = true;

	/**
	 * Scramble block texture rotation. This is the mod's reason to exist and has no gameplay or
	 * multiplayer consequence whatsoever, so it defaults on.
	 */
	public boolean scrambleRotation = true;

	/**
	 * Scramble the horizontal offset of grass, flowers, bamboo and dripstone.
	 *
	 * <p>Off by default: the offset is a genuine second coordinate leak, but vanilla also feeds it
	 * into the collision shapes of bamboo, dripstone and speleothems, and only the client is
	 * scrambled. Turning this on means those three render slightly apart from where you collide with
	 * them. Everything else that offsets - grass, flowers - has no collision and is unaffected.
	 */
	public boolean scrambleOffset = false;

	/**
	 * Derive a distinct salt per world, so a screenshot of one base does not help against another.
	 * Off falls back to one salt for every world.
	 */
	public boolean perWorldSalt = true;

	public boolean repair() {
		boolean repaired = false;

		if (schemaVersion != SCHEMA_VERSION) {
			schemaVersion = SCHEMA_VERSION;
			repaired = true;
		}

		return repaired;
	}

	public static ClientConfig load(Path file) {
		ClientConfig config = read(file);

		if (config.repair()) {
			save(config, file);
		}

		return config;
	}

	private static ClientConfig read(Path file) {
		if (!Files.isRegularFile(file)) {
			ClientConfig config = new ClientConfig();
			config.repair();
			save(config, file);
			return config;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			ClientConfig config = GSON.fromJson(reader, ClientConfig.class);
			return config == null ? new ClientConfig() : config;
		} catch (IOException | JsonSyntaxException broken) {
			// A broken config must not stop the game booting, but silently overwriting a file the
			// player may have hand-edited is rude. Keep the damaged copy alongside the new one.
			HideMyBase.LOGGER.warn("Could not read {}, regenerating defaults", file, broken);
			quarantine(file);
			return new ClientConfig();
		}
	}

	private static void quarantine(Path file) {
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Best effort only; save() below will overwrite it either way.
		}
	}

	public static void save(ClientConfig config, Path file) {
		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException failed) {
			HideMyBase.LOGGER.error("Could not write {}", file, failed);
		}
	}
}
