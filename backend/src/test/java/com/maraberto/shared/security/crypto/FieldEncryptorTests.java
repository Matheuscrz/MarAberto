package com.maraberto.shared.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldEncryptorTests {

    private static final byte[] KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_KEY =
            "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    private final FieldEncryptor encryptor = new FieldEncryptor("k1", KEY, Map.of());

    @Test
    void encryptsAndDecryptsWithAad() {
        String token = encryptor.encrypt("localização -10.9472,-37.0731", "ctx");

        assertThat(token).startsWith("enc:v1:k1:");
        assertThat(token).doesNotContain("localização");
        assertThat(encryptor.decrypt(token, "ctx")).isEqualTo("localização -10.9472,-37.0731");
    }

    @Test
    void usesRandomIvPerOperation() {
        String first = encryptor.encrypt("mesmo valor");
        String second = encryptor.encrypt("mesmo valor");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("mesmo valor");
        assertThat(encryptor.decrypt(second)).isEqualTo("mesmo valor");
    }

    @Test
    void detectsTampering() {
        String token = encryptor.encrypt("valor sensível");

        assertThatThrownBy(() -> encryptor.decrypt(token + "A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integridade");
    }

    @Test
    void bindsCiphertextToAad() {
        String token = encryptor.encrypt("valor", "contexto-a");

        assertThatThrownBy(() -> encryptor.decrypt(token, "contexto-b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotatesKeysUsingPreviousKeys() {
        FieldEncryptor old = new FieldEncryptor("k0", KEY, Map.of());
        String token = old.encrypt("legado");

        FieldEncryptor current = new FieldEncryptor("k1", OTHER_KEY, Map.of("k0", KEY));

        assertThat(current.decrypt(token)).isEqualTo("legado");
    }

    @Test
    void rejectsUnknownKeyId() {
        FieldEncryptor old = new FieldEncryptor("k0", KEY, Map.of());
        String token = old.encrypt("legado");

        assertThatThrownBy(() -> encryptor.decrypt(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desconhecida");
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThatThrownBy(() -> new FieldEncryptor("k1", new byte[16], Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void keepsLegacyPlaintextReadableForMigration() {
        assertThat(encryptor.decryptOrPlaintext("valor antigo", "ctx")).isEqualTo("valor antigo");
        assertThat(encryptor.decryptOrPlaintext(null, "ctx")).isNull();
    }
}
