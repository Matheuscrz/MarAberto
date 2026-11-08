package com.maraberto.shared.security;

/**
 * Validação de CPF pelos dígitos verificadores (não valida existência na Receita).
 *
 * <p>O CPF em claro só deve circular no request de cadastro/login; persista
 * sempre cifrado com {@code EncryptedStringConverter} e um índice cego para
 * unicidade.
 */
public final class CpfValidator {

    private CpfValidator() {
    }

    public static boolean isValid(String cpf) {
        String digits = normalize(cpf);
        if (digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }

        int first = checkDigit(digits, 9);
        int second = checkDigit(digits, 10);
        return digits.charAt(9) == Character.forDigit(first, 10)
                && digits.charAt(10) == Character.forDigit(second, 10);
    }

    public static String normalize(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += Character.getNumericValue(digits.charAt(index)) * (length + 1 - index);
        }
        int digit = 11 - (sum % 11);
        return digit >= 10 ? 0 : digit;
    }
}
