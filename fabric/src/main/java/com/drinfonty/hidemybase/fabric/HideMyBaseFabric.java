package com.drinfonty.hidemybase.fabric;

import com.drinfonty.hidemybase.client.HideMyBaseClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint. Loads the config and stops - the scramble itself is driven entirely by mixins,
 * so there is nothing to register and no Fabric API dependency.
 */
public class HideMyBaseFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HideMyBaseClient.init(FabricLoader.getInstance().getConfigDir());
	}
}
