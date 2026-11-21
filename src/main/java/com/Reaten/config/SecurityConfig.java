package com.Reaten.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;

import com.Reaten.auth.emailverification.EmailVerificationFilter;
import com.Reaten.auth.emailverification.EmailVerificationService;
import com.Reaten.shared.security.ProblemJsonAuthenticationEntryPoint;

/**
 * Segurança baseada em sessão no servidor (Spring Session JDBC) com cookie HttpOnly.
 * <ul>
 * <li>CSRF no modo SPA: cookie {@code XSRF-TOKEN} legível pelo JS + cabeçalho {@code X-XSRF-TOKEN}.</li>
 * <li>Login/cadastro são endpoints JSON próprios; a sessão é criada por {@link com.Reaten.auth.SessionAuthenticator}.</li>
 * <li>Respostas 401 em JSON, nunca redirecionamento para página de login.</li>
 * <li>Contas com e-mail não confirmado só alcançam {@code /api/auth/**} ({@link EmailVerificationFilter}).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	public static final String SESSION_COOKIE = "RETEN_SESSION";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			EmailVerificationService emailVerification, RetenProperties properties) throws Exception {
		http
				.csrf(csrf -> csrf.spa())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST,
								"/api/auth/register",
								"/api/auth/login",
								"/api/auth/password/forgot",
								"/api/auth/password/reset").permitAll()
						.requestMatchers(HttpMethod.GET,
								"/api/auth/csrf",
								"/api/spotify/callback",
								"/actuator/health").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(new ProblemJsonAuthenticationEntryPoint()))
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.deleteCookies(SESSION_COOKIE)
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.addFilterAfter(new EmailVerificationFilter(emailVerification, properties.emailVerification().required()),
						AuthorizationFilter.class)
				.requestCache(cache -> cache.disable())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * Aplicada no login: troca o id da sessão (contra fixação de sessão) e renova o token CSRF.
	 * O repositório de CSRF é o mesmo tipo e configuração usados por {@code csrf.spa()}.
	 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(CookieCsrfTokenRepository.withHttpOnlyFalse())));
	}
}
