package com.Reaten.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Transforma uma autenticação bem-sucedida em sessão persistida, como o formLogin faria.
 * <p>
 * "Manter conectado": a sessão vale por {@code reten.session.remember-me} sem uso e o cookie ganha
 * prazo, sobrevivendo ao fechamento do navegador. Sem isso, o cookie morre com o navegador e a sessão
 * expira pelo tempo padrão ({@code spring.session.timeout}).
 */
@Component
public class SessionAuthenticator {

	/** Atributo de requisição lido pelo {@code CookieSerializer} para dar prazo ao cookie (ver SessionConfig). */
	public static final String REMEMBER_ME_REQUEST_ATTRIBUTE = SessionAuthenticator.class.getName() + ".rememberMe";
	/** Guardado na sessão para que uma troca de id (ex.: troca de e-mail) mantenha a escolha. */
	static final String REMEMBER_ME_SESSION_ATTRIBUTE = SessionAuthenticator.class.getName() + ".remembered";

	private final SessionAuthenticationStrategy sessionStrategy;
	private final SecurityContextRepository contextRepository;
	private final Duration rememberMe;
	private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

	SessionAuthenticator(SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository,
			@Value("${reten.session.remember-me:30d}") Duration rememberMe) {
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
		this.rememberMe = rememberMe;
	}

	public void establish(Authentication authentication, boolean remember, HttpServletRequest request,
			HttpServletResponse response) {
		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = holderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		holderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);

		HttpSession session = request.getSession();
		if (remember) {
			session.setMaxInactiveInterval((int) rememberMe.toSeconds());
			session.setAttribute(REMEMBER_ME_SESSION_ATTRIBUTE, Boolean.TRUE);
			request.setAttribute(REMEMBER_ME_REQUEST_ATTRIBUTE, Boolean.TRUE);
		} else {
			session.removeAttribute(REMEMBER_ME_SESSION_ATTRIBUTE);
		}
	}

	/** Recria a sessão de quem já está logado, mantendo a escolha de "manter conectado". */
	public void reestablish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		HttpSession current = request.getSession(false);
		boolean remembered = current != null && Boolean.TRUE.equals(current.getAttribute(REMEMBER_ME_SESSION_ATTRIBUTE));
		establish(authentication, remembered, request, response);
	}
}
