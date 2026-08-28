package com.drinfonty.hidemybase.mixin;

import com.drinfonty.hidemybase.client.HideMyBaseClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mod's only lifecycle hook: pick up the salt for whichever world was just entered.
 *
 * <p>A mixin rather than a loader event because Fabric API and the NeoForge event bus would need two
 * adapters for one callback, and this way the mod has no dependency beyond the loader itself.
 * {@code setLevel} runs before any section of the new world is queued for meshing, which is what
 * lets {@link com.drinfonty.hidemybase.Scrambler} get away with a plain volatile handoff.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "setLevel", at = @At("TAIL"))
	private void hideMyBase$onSetLevel(ClientLevel level, CallbackInfo callback) {
		HideMyBaseClient.onLevelChanged((Minecraft) (Object) this, level != null);
	}
}
