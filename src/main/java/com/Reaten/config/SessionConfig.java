package com.Reaten.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie de sessão definido explicitamente, para valer igual em qualquer ambiente
 * (servidor embarcado, testes com MockMvc, deploy atrás de proxy).
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
		return serializer;
	}
}
