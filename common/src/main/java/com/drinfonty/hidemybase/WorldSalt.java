package com.drinfonty.hidemybase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives the per-world salt from the install secret and a world identifier.
 *
 * <p>Kept separate from Minecraft types so the derivation is testable: the point of the per-world
 * mode is that two different world keys must not produce related salts, which is a property worth
 * asserting rather than assuming.
 */
public final class WorldSalt {
	private WorldSalt() {
	}

	/**
	 * SHA-256 over {@code secret || worldKey}, folded to the 64 bits the render seed needs.
	 *
	 * <p>A plain hash rather than an HMAC: the input is not attacker-chosen in any meaningful way -
	 * a hostile server could pick its own address, but learning the salt for its own world tells it
	 * nothing about any other world's, because the secret is never revealed by the output.
	 */
	public static long derive(byte[] secret, String worldKey) {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			// SHA-256 is mandatory on every conformant JRE.
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}

		digest.update(secret);
		digest.update((byte) 0);
		digest.update(worldKey.getBytes(StandardCharsets.UTF_8));

		byte[] hash = digest.digest();
		long salt = 0L;

		for (int i = 0; i < 8; i++) {
			salt = (salt << 8) | (hash[i] & 0xFFL);
		}

		// A zero salt is indistinguishable from "not armed" in some of the hash paths, and is a
		// 1-in-2^64 accident that is far cheaper to exclude than to reason about.
		return salt == 0L ? 1L : salt;
	}
}
