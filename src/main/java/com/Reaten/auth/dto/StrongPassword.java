package com.Reaten.auth.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Senha nova (cadastro e redefinição): 8 a 72 caracteres, ao menos uma letra maiúscula e um caractere especial.
 * Uma única mensagem diz tudo o que falta, na mesma ordem da lista mostrada no formulário.
 * O limite de 72 vem do bcrypt, que ignora o que passa disso.
 */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface StrongPassword {

	String message() default "Senha fraca.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int MIN = 8;
	int MAX = 72;

	class Validator implements ConstraintValidator<StrongPassword, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null || value.isEmpty()) {
				return true; // @NotBlank cuida do vazio
			}
			if (value.length() > MAX) {
				return reject(context, "Use no máximo " + MAX + " caracteres.");
			}
			List<String> missing = new ArrayList<>();
			if (value.length() < MIN) {
				missing.add("pelo menos " + MIN + " caracteres");
			}
			if (value.codePoints().noneMatch(Character::isUpperCase)) {
				missing.add("uma letra maiúscula");
			}
			if (value.codePoints().noneMatch(Validator::isSpecial)) {
				missing.add("um caractere especial (ex.: ! @ # -)");
			}
			if (missing.isEmpty()) {
				return true;
			}
			String list = missing.size() == 1 ? missing.get(0)
					: String.join(", ", missing.subList(0, missing.size() - 1)) + " e " + missing.getLast();
			return reject(context, "A senha precisa ter " + list + ".");
		}

		static boolean isSpecial(int codePoint) {
			return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
		}

		private static boolean reject(ConstraintValidatorContext context, String message) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
			return false;
		}
	}
}
