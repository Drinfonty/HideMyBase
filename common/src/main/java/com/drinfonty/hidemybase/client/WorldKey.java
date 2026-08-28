package com.drinfonty.hidemybase.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;

/**
 * A stable identifier for "the world this client is in", used to give each world its own salt.
 *
 * <p>It only has to be stable across sessions and distinct between worlds - it is hashed with the
 * secret and never shown, so it need not be unguessable. Guessing it gains nothing without the
 * secret.
 */
public final class WorldKey {
	private WorldKey() {
	}

	public static String of(Minecraft minecraft) {
		MinecraftServer singleplayer = minecraft.getSingleplayerServer();

		if (singleplayer != null) {
			// The save's display name. Two saves can share one, in which case they share a salt -
			// harmless, since the salt is still secret and still differs from every other world's.
			return "sp:" + singleplayer.getWorldData().getLevelName();
		}

		ServerData server = minecraft.getCurrentServer();

		if (server != null && server.ip != null) {
			return "mp:" + server.ip;
		}

		// Realms, direct-connect edge cases, or a connection torn down mid-derivation. A shared
		// fallback key is still salted by the secret, so this degrades to "one salt for these"
		// rather than to vanilla.
		return "unknown";
	}
}
