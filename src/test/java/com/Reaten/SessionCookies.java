package com.Reaten;

import java.util.List;

import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

/**
 * O Spring Session grava o cookie diretamente no cabeçalho Set-Cookie (para suportar SameSite),
 * então nos testes o lemos do cabeçalho em vez de {@code getCookie}.
 */
public final class SessionCookies {

	public static final String NAME = "RETEN_SESSION";

	private SessionCookies() {
	}

	public static Cookie from(MockHttpServletResponse response) {
		List<String> headers = response.getHeaders("Set-Cookie");
		return headers.stream()
				.filter(h -> h.startsWith(NAME + "="))
				.findFirst()
				.map(h -> {
					Cookie cookie = new Cookie(NAME, h.substring(NAME.length() + 1, h.indexOf(';')));
					cookie.setHttpOnly(h.contains("HttpOnly"));
					return cookie;
				})
				.orElse(null);
	}
}
