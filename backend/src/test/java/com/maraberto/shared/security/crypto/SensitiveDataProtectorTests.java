package com.maraberto.shared.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SensitiveDataProtectorTests {

    private static final byte[] KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final SensitiveDataProtector protector = new SensitiveDataProtector(
            new FieldEncryptor("k1", KEY, java.util.Map.of()), new BlindIndexer(KEY));

    @Test
    void masksCpfPreservingMiddleDigits() {
        assertThat(protector.maskCpf("529.982.247-25")).isEqualTo("***.982.247-**");
        assertThat(protector.maskCpf("123")).isEqualTo(SensitiveDataProtector.REDACTED);
    }

    @Test
    void masksEmailAndPhone() {
        assertThat(protector.maskEmail("banhista@example.com")).isEqualTo("b***@example.com");
        assertThat(protector.maskPhone("+55 (11) 99999-1234")).isEqualTo("***1234");
    }

    @Test
    void reducesLocationPrecisionForLogs() {
        assertThat(protector.redactLocation(-10.947234, -37.073198))
                .isEqualTo("-10.947,-37.073");
    }

    @Test
    void masksIpAddress() {
        assertThat(protector.redactIp("192.168.10.42")).isEqualTo("192.168.10.***");
        assertThat(protector.redactIp(null)).isEqualTo(SensitiveDataProtector.REDACTED);
    }

    @Test
    void exposesEncryptionAndBlindIndex() {
        String token = protector.encrypt("segredo", "ctx");
        assertThat(protector.decrypt(token, "ctx")).isEqualTo("segredo");
        assertThat(protector.blindIndex("segredo")).isEqualTo(protector.blindIndex("segredo"));
        assertThat(protector.keyId()).isEqualTo("k1");
    }
}
