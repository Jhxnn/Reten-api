package com.Reaten.shared.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Responde 401 no mesmo formato RFC 9457 usado pelo resto da API. */
public class ProblemJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final String BODY = """
			{"type":"about:blank","title":"Unauthorized","status":401,\
			"detail":"Entre na sua conta para continuar.","code":"unauthenticated"}""";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(BODY);
	}
}
