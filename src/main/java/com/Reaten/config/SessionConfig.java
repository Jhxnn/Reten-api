package com.Reaten.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import com.Reaten.auth.SessionAuthenticator;

/**
 * Cookie de sessão definido explicitamente, para valer igual em qualquer ambiente
 * (servidor embarcado, testes com MockMvc, deploy atrás de proxy).
 * Sem "manter conectado" o cookie não tem prazo (some ao fechar o navegador); com ele, o cookie ganha
 * prazo longo e quem limita a duração é a sessão no servidor ({@code reten.session.remember-me}).
 */
@Configuration
public class SessionConfig {

	@Bean
	CookieSerializer cookieSerializer(@Value("${reten.session.cookie-secure:false}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SecurityConfig.SESSION_COOKIE);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secure);
		serializer.setRememberMeRequestAttribute(SessionAuthenticator.REMEMBER_ME_REQUEST_ATTRIBUTE);
		return serializer;
	}
}
