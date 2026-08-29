package com.drinfonty.hidemybase.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one part of the settings screen that genuinely differs between Minecraft versions.
 *
 * <p>Switching screens was renamed mid-range and the two names do not overlap: 1.21.8 and earlier
 * have only {@code setScreen}, 26.2 has only {@code setScreenAndShow}, and 1.21.9 - 26.1 have both.
 * There is therefore no single call that compiles everywhere.
 *
 * <p>Isolating it here keeps {@link HideMyBaseConfigScreen} - the whole layout, all the behaviour,
 * every string - identical on all six branches, so this file is the only thing to re-check when
 * porting the screen.
 *
 * <p>This branch targets 26.2.
 */
final class ScreenCompat {
	private ScreenCompat() {
	}

	static void setScreen(Minecraft minecraft, Screen screen) {
		if (minecraft != null) {
			minecraft.setScreenAndShow(screen);
		}
	}
}
