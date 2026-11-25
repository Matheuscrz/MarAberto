package com.maraberto.identity.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Gera a senha temporária entregue pela TI.
 *
 * <p>16 caracteres de um alfabeto sem ambíguos (sem {@code I}, {@code l},
 * {@code O}, {@code 0}, {@code 1}) — o valor é lido em voz alta ou copiado à
 * mão, e a troca no primeiro acesso é obrigatória.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] value = new char[LENGTH];
        for (int index = 0; index < LENGTH; index += 1) {
            value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
