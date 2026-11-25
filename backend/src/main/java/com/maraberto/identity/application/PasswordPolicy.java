package com.maraberto.identity.application;

import com.maraberto.identity.domain.WeakPasswordException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Política da senha definitiva.
 *
 * <p>Exige tamanho mínimo (configurável, 12 por padrão), presença de letra e
 * dígito e recusa as senhas óbvias mais comuns. Não exige símbolo: o ganho de
 * entropia não compensa a dificuldade de digitar em campo.
 */
@Component
public class PasswordPolicy {

    private static final Set<String> OBVIOUS = Set.of(
            "123456789012",
            "1234567890123",
            "senha123456",
            "senha12345678",
            "password1234",
            "qwertyuiop12",
            "maraberto123",
            "admin1234567",
            "demo12345678");

    private final int minLength;

    public PasswordPolicy(
            @Value("${maraberto.identity.password-min-length:12}") int minLength) {
        this.minLength = Math.max(minLength, 8);
    }

    public void validate(String newPassword, String currentPassword) {
        String value = newPassword == null ? "" : newPassword;
        if (value.length() < minLength) {
            throw new WeakPasswordException(
                    "A nova senha precisa ter pelo menos " + minLength + " caracteres.");
        }
        if (value.equals(currentPassword)) {
            throw new WeakPasswordException("A nova senha precisa ser diferente da atual.");
        }
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new WeakPasswordException("A nova senha precisa ter letras e números.");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (OBVIOUS.contains(normalized) || normalized.contains("maraberto")) {
            throw new WeakPasswordException("Escolha uma senha menos previsível.");
        }
    }

    public int minLength() {
        return minLength;
    }
}
