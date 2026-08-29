package com.drinfonty.hidemybase.client;

import java.nio.file.Path;

import com.drinfonty.hidemybase.HideMyBase;
import com.drinfonty.hidemybase.Scrambler;
import com.drinfonty.hidemybase.WorldSalt;
import com.drinfonty.hidemybase.config.ClientConfig;

import net.minecraft.client.Minecraft;

/**
 * Mod lifecycle, shared by both loaders.
 *
 * <p>The loader entrypoints do nothing but call {@link #init}; everything after that is driven by a
 * mixin on {@code Minecraft.setLevel}, so neither Fabric API nor a NeoForge event bus is needed and
 * the two loaders cannot drift apart in behaviour.
 */
public final class HideMyBaseClient {
	private static ClientConfig config = new ClientConfig();
	private static Path configFile;

	private HideMyBaseClient() {
	}

	public static void init(Path configDirectory) {
		configFile = configDirectory.resolve(HideMyBase.MOD_ID + ".json");
		config = ClientConfig.load(configFile);
		HideMyBase.LOGGER.info("{} ready (rotation={}, offset={}, perWorldSalt={})", HideMyBase.MOD_NAME,
			config.enabled && config.scrambleRotation, config.enabled && config.scrambleOffset,
			config.perWorldSalt);
	}

	public static ClientConfig config() {
		return config;
	}

	/**
	 * Called from {@code LevelLifecycleMixin} once a level is set, and with {@code null} when one is torn
	 * down. Running before any chunk of the new world has been meshed is what makes the volatile
	 * handoff in {@link Scrambler} sufficient.
	 */
	public static void onLevelChanged(Minecraft minecraft, boolean hasLevel) {
		if (!hasLevel) {
			Scrambler.leave();
			return;
		}

		apply(minecraft);
	}

	/** Arm the scrambler for the world the client is currently in. */
	public static void apply(Minecraft minecraft) {
		byte[] secret = config.decodeSecret();

		if (!config.enabled || secret == null) {
			Scrambler.leave();
			return;
		}

		String worldKey = config.perWorldSalt ? WorldKey.of(minecraft) : "";
		long salt = WorldSalt.derive(secret, worldKey);

		Scrambler.enter(salt, config.scrambleRotation, config.scrambleOffset);
	}

	/**
	 * Persist the settings the screen just changed, re-arm, and re-mesh what is already drawn.
	 *
	 * <p>Distinct from {@link #reload()}, which re-reads the file from disk: here the in-memory
	 * config is the newer copy and the file is the stale one, so reading it back would undo the
	 * change that was just made.
	 */
	public static void applyAndSave() {
		if (configFile != null) {
			ClientConfig.save(config, configFile);
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null) {
			Scrambler.leave();
			return;
		}

		apply(minecraft);
		RenderRefresh.rebuildAll(minecraft);
	}

	/**
	 * Re-arm and rebuild the world after a settings change. Only reachable from a config edit today,
	 * but the rebuild has to exist somewhere: chunks meshed under the old salt keep their old
	 * rotations until something invalidates them.
	 */
	public static void reload() {
		if (configFile != null) {
			config = ClientConfig.load(configFile);
		}

		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level == null) {
			Scrambler.leave();
			return;
		}

		apply(minecraft);
		RenderRefresh.rebuildAll(minecraft);
	}
}
