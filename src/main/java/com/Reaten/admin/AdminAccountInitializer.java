package com.Reaten.admin;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.Reaten.config.RetenProperties;
import com.Reaten.user.Emails;
import com.Reaten.user.User;
import com.Reaten.user.UserRepository;

/**
 * Garante a conta de administrador a cada inicialização, depois do Flyway:
 * num banco novo ela nasce junto; se o e-mail já pertence a alguém, essa conta vira admin
 * (a senha dela não muda).
 */
@Component
class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

	private final RetenProperties properties;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;

	AdminAccountInitializer(RetenProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		RetenProperties.Admin admin = properties.admin();
		if (admin == null || !admin.configured()) {
			log.info("Conta de administrador não configurada (RETEN_ADMIN_EMAIL e RETEN_ADMIN_PASSWORD).");
			return;
		}
		String email = Emails.normalize(admin.email());
		User user = users.findByEmail(email).orElse(null);
		if (user == null) {
			user = new User("Administrador", email, passwordEncoder.encode(admin.password()));
			user.markEmailVerified(Instant.now());
			user.promoteToAdmin();
			users.save(user);
			log.info("Conta de administrador criada.");
		} else if (!user.isAdmin()) {
			user.promoteToAdmin();
			user.markEmailVerified(Instant.now());
			log.info("Conta existente promovida a administrador.");
		}
	}
}
