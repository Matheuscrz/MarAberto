package com.maraberto.shared.security.sanitization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TextSanitizerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stripsMarkupAndScriptsFromPlainText() {
        String sanitized = TextSanitizer.sanitize(
                "<script>alert(1)</script>Olá <b>mundo</b>", SanitizationPolicy.PLAIN_TEXT);

        assertThat(sanitized).contains("Olá").contains("mundo");
        assertThat(sanitized).doesNotContain("<script").doesNotContain("<b>");
    }

    @Test
    void decodesEntitiesAndRemovesControlCharacters() {
        String sanitized = TextSanitizer.sanitize(
                "Tom &amp; Jerry\u0000\u0007", SanitizationPolicy.PLAIN_TEXT);

        assertThat(sanitized).isEqualTo("Tom & Jerry");
    }

    @Test
    void enforcesMaximumLength() {
        assertThatThrownBy(() -> TextSanitizer.sanitize(
                "abcdef", SanitizationPolicy.PLAIN_TEXT, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamanho máximo");
    }

    @Test
    void validatesIdentifiers() {
        assertThat(TextSanitizer.sanitize(
                "8d6a5a87-1c43-4af4-9b7f-9f2d5a5a4f5c", SanitizationPolicy.IDENTIFIER))
                .isEqualTo("8d6a5a87-1c43-4af4-9b7f-9f2d5a5a4f5c");

        assertThatThrownBy(() -> TextSanitizer.sanitize(
                "<script>alert(1)</script>", SanitizationPolicy.IDENTIFIER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesEmailAndRejectsInvalid() {
        assertThat(TextSanitizer.sanitize(" Foo@Bar.COM ", SanitizationPolicy.EMAIL))
                .isEqualTo("foo@bar.com");
        assertThatThrownBy(() -> TextSanitizer.sanitize("nao-e-email", SanitizationPolicy.EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesPhoneAndCpf() {
        assertThat(TextSanitizer.sanitize("+55 (11) 99999-9999", SanitizationPolicy.PHONE))
                .isEqualTo("+55 (11) 99999-9999");
        assertThat(TextSanitizer.sanitize("529.982.247-25", SanitizationPolicy.CPF))
                .isEqualTo("52998224725");
        assertThatThrownBy(() -> TextSanitizer.sanitize("529.982.247-24", SanitizationPolicy.CPF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDangerousUrls() {
        assertThat(TextSanitizer.sanitize("https://example.com/praia", SanitizationPolicy.URL))
                .isEqualTo("https://example.com/praia");
        assertThatThrownBy(() -> TextSanitizer.sanitize(
                "javascript:alert(1)", SanitizationPolicy.URL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextSanitizer.sanitize(
                "https://user:pass@example.com", SanitizationPolicy.URL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesYoutubeId() {
        assertThat(TextSanitizer.sanitize("dQw4w9WgXcQ", SanitizationPolicy.YOUTUBE_ID))
                .isEqualTo("dQw4w9WgXcQ");
        assertThatThrownBy(() -> TextSanitizer.sanitize(
                "https://youtube.com/watch?v=dQw4w9WgXcQ", SanitizationPolicy.YOUTUBE_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void richTextAllowsOnlySafeMarkup() {
        String sanitized = TextSanitizer.sanitize(
                "<p>Olá <strong>mundo</strong></p>"
                        + "<script>alert(1)</script>"
                        + "<a href=\"javascript:alert(1)\" onclick=\"alert(1)\">x</a>"
                        + "<a href=\"https://example.com\">ok</a>",
                SanitizationPolicy.RICH_TEXT);

        assertThat(sanitized).contains("<p>").contains("<strong>");
        assertThat(sanitized).contains("https://example.com");
        assertThat(sanitized).doesNotContain("javascript:").doesNotContain("onclick");
        assertThat(sanitized).doesNotContain("<script");
    }

    @Test
    void sanitizedAnnotationAppliesPolicyDuringJsonDeserialization() throws Exception {
        record PlainPayload(@Sanitized(value = SanitizationPolicy.PLAIN_TEXT, maxLength = 64)
                            String text) {
        }
        record IdPayload(@Sanitized(value = SanitizationPolicy.IDENTIFIER, maxLength = 64)
                         String id) {
        }

        PlainPayload plain = objectMapper.readValue(
                "{\"text\":\"<script>alert(1)</script>Olá\"}", PlainPayload.class);
        assertThat(plain.text()).contains("Olá").doesNotContain("<script");

        IdPayload script = objectMapper.readValue(
                "{\"id\":\"<script>alert(1)</script>\"}", IdPayload.class);
        assertThat(script.id()).isEmpty();

        IdPayload spaces = objectMapper.readValue(
                "{\"id\":\"id com espaço\"}", IdPayload.class);
        assertThat(spaces.id()).isEmpty();
    }
}
