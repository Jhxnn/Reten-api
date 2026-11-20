package com.Reaten.spotify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.shared.exception.ApiException;
import com.Reaten.shared.security.TokenCipher;
import com.Reaten.spotify.client.SpotifyApiException;
import com.Reaten.spotify.client.SpotifyAuthClient;
import com.Reaten.spotify.client.SpotifyAuthClient.SpotifyProfile;
import com.Reaten.spotify.client.SpotifyAuthClient.TokenGrant;

/**
 * Guarda a conexão com o Spotify e entrega access tokens válidos, renovando-os quando necessário.
 * Nenhum token sai deste serviço para o frontend.
 */
@Service
public class SpotifyAccountService {

	private static final Logger log = LoggerFactory.getLogger(SpotifyAccountService.class);

	/** Renova com folga para não usar um token que expira no meio de uma requisição. */
	private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

	private final SpotifyAccountRepository accounts;
	private final SpotifyAuthClient client;
	private final TokenCipher cipher;

	SpotifyAccountService(SpotifyAccountRepository accounts, SpotifyAuthClient client, TokenCipher cipher) {
		this.accounts = accounts;
		this.client = client;
		this.cipher = cipher;
	}

	@Transactional(readOnly = true)
	public Optional<SpotifyAccount> findConnection(UUID userId) {
		return accounts.findByUserId(userId);
	}

	@Transactional
	void saveConnection(UUID userId, TokenGrant grant, SpotifyProfile profile) {
		if (grant.refreshToken() == null) {
			throw new SpotifyApiException(SpotifyApiException.Kind.UNAVAILABLE, "Spotify não enviou refresh token", null, null);
		}
		SpotifyAccount account = accounts.findByUserId(userId).orElseGet(() -> new SpotifyAccount(userId));
		account.link(profile.id(), profile.displayName(),
				cipher.encrypt(grant.accessToken()),
				cipher.encrypt(grant.refreshToken()),
				grant.scope(),
				Instant.now().plus(grant.expiresIn()));
		accounts.save(account);
		log.info("Conta Spotify conectada ao usuário {}", userId);
	}

	@Transactional
	public void disconnect(UUID userId) {
		accounts.deleteByUserId(userId);
		log.info("Conta Spotify desconectada do usuário {}", userId);
	}

	/**
	 * Access token pronto para uso. Renova sob lock pessimista para que requisições paralelas
	 * não gastem o mesmo refresh token. Se a autorização foi revogada, a conexão é removida e o
	 * usuário precisa conectar de novo.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
	public String validAccessToken(UUID userId) {
		SpotifyAccount account = accounts.findByUserIdForUpdate(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "spotify_not_connected",
						"Conecte sua conta Spotify para continuar."));

		if (Instant.now().plus(REFRESH_MARGIN).isBefore(account.getExpiresAt())) {
			return cipher.decrypt(account.getAccessTokenEncrypted());
		}

		try {
			TokenGrant grant = client.refresh(cipher.decrypt(account.getRefreshTokenEncrypted()));
			account.updateTokens(
					cipher.encrypt(grant.accessToken()),
					grant.refreshToken() == null ? null : cipher.encrypt(grant.refreshToken()),
					grant.scope(),
					Instant.now().plus(grant.expiresIn()));
			return grant.accessToken();
		} catch (SpotifyApiException ex) {
			if (ex.getKind() == SpotifyApiException.Kind.INVALID_GRANT) {
				accounts.delete(account);
				log.warn("Refresh token do Spotify revogado para o usuário {}; conexão removida", userId);
				throw new ApiException(HttpStatus.CONFLICT, "spotify_reauthorization_required",
						"Sua conexão com o Spotify expirou. Conecte novamente para continuar.");
			}
			throw SpotifyErrors.toApiException(ex);
		}
	}
}
