package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.identity.domain.OperationalUserFieldConverters;
import com.maraberto.identity.infra.security.JwtProperties;
import com.maraberto.identity.infra.security.TokenRevocationService;
import com.maraberto.identity.infra.security.TotpGenerator;
import com.maraberto.shared.security.crypto.EncryptedStringConverter;
import com.maraberto.shared.security.crypto.FieldEncryptor;
import com.maraberto.shared.security.crypto.SensitiveDataProtector;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class TotpAndRevocationTests {

    @Test
    void totpGeneratorProducesValidCodesWithinWindow() {
        TotpGenerator generator = new TotpGenerator();
        String secret = generator.generateSecret();
        Instant now = Instant.parse("2026-11-25T15:00:00Z");

        String code = generator.currentCode(secret, now);
        assertThat(code).matches("\\d{6}");
        assertThat(generator.verifyCode(secret, code, now)).isTrue();
        assertThat(generator.verifyCode(secret, code, now.plusSeconds(30))).isTrue();
        assertThat(generator.verifyCode(secret, code, now.minusSeconds(30))).isTrue();
        assertThat(generator.verifyCode(secret, code, now.plusSeconds(120))).isFalse();
        assertThat(generator.verifyCode(secret, "000000", now)).isFalse();

        String uri = generator.buildOtpAuthUri("MarAberto", "ADMIN", secret);
        assertThat(uri).startsWith("otpauth://totp/MarAberto:ADMIN?secret=" + secret);
    }

    @Test
    void tokenRevocationServiceRejectsRevokedJtiAndUserCutoff() {
        JwtProperties props = new JwtProperties();
        props.setAccessTokenTtl(Duration.ofMinutes(15));
        ObjectProvider<StringRedisTemplate> emptyProvider =
                new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class);
        TokenRevocationService service = new TokenRevocationService(emptyProvider, props);

        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(10);

        assertThat(service.isRevoked("jti-1", userId, issuedAt)).isFalse();

        service.revokeToken("jti-1", Instant.now().plusSeconds(300));
        assertThat(service.isRevoked("jti-1", userId, issuedAt)).isTrue();
        assertThat(service.isRevoked("jti-2", userId, issuedAt)).isFalse();

        service.revokeAllForUser(userId, Instant.now());
        assertThat(service.isRevoked("jti-2", userId, issuedAt)).isTrue();
        assertThat(service.isRevoked("jti-3", userId, Instant.now().plusSeconds(5))).isFalse();
    }

    @Test
    void columnScopedAadPreventsCrossColumnCiphertextSwapAndReadsLegacy() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        FieldEncryptor encryptor = new FieldEncryptor("k1", key, Map.of());
        EncryptedStringConverter.configure(encryptor);

        var emailConverter = new OperationalUserFieldConverters.EmailConverter();
        var cpfConverter = new OperationalUserFieldConverters.CpfConverter();

        String encryptedEmail = emailConverter.convertToDatabaseColumn("admin@maraberto.local");
        assertThat(emailConverter.convertToEntityAttribute(encryptedEmail)).isEqualTo("admin@maraberto.local");

        // Trocar ciphertext de email para a coluna CPF deve falhar na autenticação GCM.
        assertThatThrownBy(() -> cpfConverter.convertToEntityAttribute(encryptedEmail))
                .isInstanceOf(IllegalArgumentException.class);

        // Compatibilidade retroativa: registro legado cifrado com AAD_JPA_FIELD continua legível.
        String legacyCiphertext = encryptor.encrypt("legado@maraberto.local", SensitiveDataProtector.AAD_JPA_FIELD);
        assertThat(emailConverter.convertToEntityAttribute(legacyCiphertext))
                .isEqualTo("legado@maraberto.local");
    }
}
