package com.Reaten.user;

import java.util.Locale;

public final class Emails {

	private Emails() {
	}

	/** Forma canônica usada para armazenar e comparar e-mails. */
	public static String normalize(String email) {
		return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}
}
