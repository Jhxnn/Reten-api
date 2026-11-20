package com.Reaten.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Transforma uma autenticação bem-sucedida em sessão persistida, como o formLogin faria. */
@Component
public class SessionAuthenticator {

	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	SessionAuthenticator(SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
	}

	public void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		holderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
	}
}
