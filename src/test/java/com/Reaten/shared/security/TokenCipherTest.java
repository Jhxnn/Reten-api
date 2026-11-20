package com.Reaten.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenCipherTest {

	private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	private final TokenCipher cipher = new TokenCipher(KEY);

	@Test
	void roundTripsAndNeverStoresPlaintext() {
		String encrypted = cipher.encrypt("BQD-spotify-access-token");

		assertThat(encrypted).startsWith("v1:").doesNotContain("spotify");
		assertThat(cipher.decrypt(encrypted)).isEqualTo("BQD-spotify-access-token");
	}

	@Test
	void usesRandomIvSoEqualInputsProduceDifferentCiphertexts() {
		assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
	}

	@Test
	void rejectsTamperedCiphertext() {
		String encrypted = cipher.encrypt("token");
		char last = encrypted.charAt(encrypted.length() - 2);
		String tampered = encrypted.substring(0, encrypted.length() - 2) + (last == 'A' ? 'B' : 'A') + encrypted.charAt(encrypted.length() - 1);

		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsKeysThatAreNotAes256() {
		assertThatThrownBy(() -> new TokenCipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
	}
}
