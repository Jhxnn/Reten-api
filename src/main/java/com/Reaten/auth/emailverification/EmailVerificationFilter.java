package com.Reaten.auth.emailverification;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.Reaten.auth.AuthenticatedUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Enquanto o e-mail não é confirmado, a conta só alcança {@code /api/auth/**} (sessão, código, troca de e-mail).
 * O resto da API responde 403 com {@code code: email_not_verified}.
 * <p>
 * A confirmação é lida do banco, não do principal da sessão (que não muda quando o código é aceito).
 * Depois de confirmada, a sessão guarda essa informação para não consultar o banco a cada requisição.
 * Não é um {@code @Component}: é registrado só na cadeia do Spring Security, não como filtro global.
 */
public class EmailVerificationFilter extends OncePerRequestFilter {

	static final String VERIFIED_ATTRIBUTE = EmailVerificationFilter.class.getName() + ".verified";

	private static final String BODY = """
			{"type":"about:blank","title":"Forbidden","status":403,\
			"detail":"Confirme seu e-mail para continuar.","code":"email_not_verified"}""";

	private final EmailVerificationService verification;
	private final boolean required;

	public EmailVerificationFilter(EmailVerificationService verification, boolean required) {
		this.verification = verification;
		this.required = required;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !required || !path.startsWith("/api/") || path.startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			chain.doFilter(request, response);
			return;
		}
		HttpSession session = request.getSession(false);
		boolean known = session != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED_ATTRIBUTE));
		if (!known) {
			if (!verification.isVerified(user.id())) {
				response.setStatus(HttpStatus.FORBIDDEN.value());
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				response.getWriter().write(BODY);
				return;
			}
			if (session != null) {
				session.setAttribute(VERIFIED_ATTRIBUTE, Boolean.TRUE);
			}
		}
		chain.doFilter(request, response);
	}
}
