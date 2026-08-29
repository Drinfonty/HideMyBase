package com.drinfonty.hidemybase.neoforge;

import com.drinfonty.hidemybase.HideMyBase;
import com.drinfonty.hidemybase.client.HideMyBaseClient;
import com.drinfonty.hidemybase.client.gui.HideMyBaseConfigScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint: loads the config and registers the settings screen.
 *
 * <p>The scramble itself is all mixins, so nothing is registered on the event bus and the two
 * loaders run identical code apart from how each exposes the settings screen - the Config button in
 * NeoForge's mods list here, ModMenu on Fabric.
 */
@Mod(value = HideMyBase.MOD_ID, dist = Dist.CLIENT)
public class HideMyBaseNeoForge {
	public HideMyBaseNeoForge(ModContainer container) {
		HideMyBaseClient.init(FMLPaths.CONFIGDIR.get());

		// The Config button beside the mod in NeoForge's own mods list. Unlike the screen itself,
		// this registration is identical on every version in range, so it stays in the shared
		// entry point.
		container.registerExtensionPoint(IConfigScreenFactory.class,
			(minecraft, parent) -> new HideMyBaseConfigScreen(parent));
	}
}
