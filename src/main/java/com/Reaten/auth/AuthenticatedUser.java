package com.Reaten.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.Reaten.user.User;

/**
 * Principal guardado na sessão. É serializado no Postgres pelo Spring Session,
 * por isso carrega apenas o mínimo — e o hash da senha é apagado após a autenticação.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final UUID id;
	private final String name;
	private final String email;
	private String passwordHash;

	AuthenticatedUser(User user) {
		this.id = user.getId();
		this.name = user.getName();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String email() {
		return email;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}
}
