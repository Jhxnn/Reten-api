package com.Reaten.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.Reaten.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Registra o último acesso de quem está logado, para a área de administração.
 * Escreve no banco no máximo uma vez por hora por sessão (o momento fica guardado na própria sessão).
 */
public final class LastSeenFilter extends OncePerRequestFilter {

	static final String SESSION_ATTRIBUTE = LastSeenFilter.class.getName() + ".touchedAt";
	private static final Duration INTERVAL = Duration.ofHours(1);

	private final UserRepository users;
	private final Clock clock;

	public LastSeenFilter(UserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		HttpSession session = request.getSession(false);
		if (session != null && authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			Instant now = clock.instant();
			if (!(session.getAttribute(SESSION_ATTRIBUTE) instanceof Instant touched) || touched.plus(INTERVAL).isBefore(now)) {
				users.touchLastSeen(user.id(), now);
				session.setAttribute(SESSION_ATTRIBUTE, now);
			}
		}
		chain.doFilter(request, response);
	}
}
