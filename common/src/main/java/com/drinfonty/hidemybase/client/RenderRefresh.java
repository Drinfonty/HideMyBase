package com.drinfonty.hidemybase.client;

import com.drinfonty.hidemybase.HideMyBase;

import net.minecraft.client.Minecraft;

/**
 * Forces a full chunk re-mesh so a salt change takes effect on already-built geometry.
 *
 * <p>Isolated here and guarded because this is the one piece of the mod that is version-fragile:
 * every version up to and including 26.1 exposes {@code allChanged()}, while 26.2 replaced it with
 * {@code invalidateCompiledGeometry(...)}. Failing here should cost the player a rejoin, not a
 * crash.
 */
public final class RenderRefresh {
	private RenderRefresh() {
	}

	public static void rebuildAll(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}

		try {
			minecraft.levelRenderer.allChanged();
		} catch (LinkageError | RuntimeException unavailable) {
			HideMyBase.LOGGER.warn("Could not force a chunk rebuild; rejoin the world to apply changes",
				unavailable);
		}
	}
}
