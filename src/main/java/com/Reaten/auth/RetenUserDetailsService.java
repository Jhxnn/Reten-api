package com.Reaten.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.user.Emails;
import com.Reaten.user.UserRepository;

@Service
class RetenUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	RetenUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(Emails.normalize(email))
				.map(AuthenticatedUser::new)
				.orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
	}
}
