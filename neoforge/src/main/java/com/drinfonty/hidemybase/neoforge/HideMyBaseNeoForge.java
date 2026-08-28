package com.drinfonty.hidemybase.neoforge;

import com.drinfonty.hidemybase.HideMyBase;
import com.drinfonty.hidemybase.client.HideMyBaseClient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge entrypoint. Like the Fabric one it only loads the config; the scramble is all mixins, so
 * nothing is registered on the event bus and the two loaders run identical code.
 */
@Mod(value = HideMyBase.MOD_ID, dist = Dist.CLIENT)
public class HideMyBaseNeoForge {
	public HideMyBaseNeoForge() {
		HideMyBaseClient.init(FMLPaths.CONFIGDIR.get());
	}
}
