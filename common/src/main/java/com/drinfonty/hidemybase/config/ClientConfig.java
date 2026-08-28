package com.drinfonty.hidemybase.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

import com.drinfonty.hidemybase.HideMyBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * {@code config/hidemybase.json}. Client-only by nature: every setting here changes what this
 * client draws and nothing that any server can observe.
 *
 * <p>The {@code secret} is the one field that matters for the mod's purpose. It is generated once
 * from {@link SecureRandom} and never leaves the machine; anyone who has it can reverse the
 * scramble, so it is written with the same care as the rest of the file and no more - this protects
 * against someone reading a screenshot, not against someone reading the disk.
 */
public final class ClientConfig {
	public static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int SECRET_BYTES = 16;

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

	/** Hex-encoded install secret. Regenerated if absent or malformed; delete it to reroll. */
	public String secret = "";

	public boolean repair() {
		boolean repaired = false;

		if (schemaVersion != SCHEMA_VERSION) {
			schemaVersion = SCHEMA_VERSION;
			repaired = true;
		}

		if (decodeSecret() == null) {
			secret = generateSecret();
			repaired = true;
		}

		return repaired;
	}

	/** The install secret as bytes, or {@code null} if the stored value is unusable. */
	public byte[] decodeSecret() {
		if (secret == null || secret.length() != SECRET_BYTES * 2) {
			return null;
		}

		try {
			return HexFormat.of().parseHex(secret);
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}

	private static String generateSecret() {
		byte[] bytes = new byte[SECRET_BYTES];
		new SecureRandom().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
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
			// A broken config must not cost the player their secret silently, but it also must not
			// stop the game booting. Keep the damaged file alongside the regenerated one.
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
