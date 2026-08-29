package com.drinfonty.hidemybase.fabric;

import com.drinfonty.hidemybase.client.gui.HideMyBaseConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Puts the settings screen behind the config button in ModMenu's mod list.
 *
 * <p>ModMenu is a compile-only dependency and an optional one at runtime: Fabric itself has no mod
 * list, so without ModMenu there is simply nowhere to put this and the entrypoint is never loaded.
 * The mod works either way - this only affects whether the settings are reachable in game.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HideMyBaseConfigScreen::new;
	}
}
