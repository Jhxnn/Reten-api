package com.Reaten.spotify.client;

import java.time.Duration;

/**
 * Porta para os endpoints de autorização e perfil da Spotify.
 * Isola o restante do sistema dos detalhes HTTP e permite substituir a implementação em testes.
 */
public interface SpotifyAuthClient {

	String authorizeUrl(String state);

	TokenGrant exchangeCode(String code);

	TokenGrant refresh(String refreshToken);

	SpotifyProfile currentProfile(String accessToken);

	/**
	 * @param refreshToken pode vir nulo numa renovação — a Spotify só envia um novo quando rotaciona o token
	 */
	record TokenGrant(String accessToken, String refreshToken, String scope, Duration expiresIn) {
	}

	record SpotifyProfile(String id, String displayName) {
	}
}
