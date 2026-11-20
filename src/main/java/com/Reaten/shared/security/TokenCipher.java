package com.Reaten.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.Reaten.config.RetenProperties;

/**
 * Cifra simétrica (AES-256-GCM) para segredos de terceiros guardados no banco, como tokens OAuth.
 * <p>
 * Formato: {@code v1:<base64(iv(12) || ciphertext+tag)>}. O prefixo de versão permite trocar
 * algoritmo ou chave no futuro sem ambiguidade.
 */
@Component
public class TokenCipher {

	private static final String VERSION = "v1:";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public TokenCipher(RetenProperties properties) {
		this(properties.security().tokenEncryptionKey());
	}

	TokenCipher(String base64Key) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key);
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("RETEN_TOKEN_ENCRYPTION_KEY precisa estar em Base64.", ex);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("RETEN_TOKEN_ENCRYPTION_KEY precisa ter 32 bytes (AES-256). Gere com: openssl rand -base64 32");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	public String encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] payload = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, payload, 0, iv.length);
			System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
			return VERSION + Base64.getEncoder().encodeToString(payload);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Falha ao cifrar token", ex);
		}
	}

	public String decrypt(String stored) {
		if (stored == null || !stored.startsWith(VERSION)) {
			throw new IllegalArgumentException("Formato de token cifrado desconhecido");
		}
		try {
			byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION.length()));
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
			byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
			return new String(plain, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Falha ao decifrar token (chave incorreta ou dado adulterado)", ex);
		}
	}
}
