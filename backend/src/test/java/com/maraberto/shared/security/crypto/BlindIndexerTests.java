package com.maraberto.shared.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BlindIndexerTests {

    private static final byte[] KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final BlindIndexer indexer = new BlindIndexer(KEY);

    @Test
    void producesDeterministicIndexForNormalizedValue() {
        String first = indexer.index("  Foo@Bar.COM ");
        String second = indexer.index("foo@bar.com");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
        assertThat(first).doesNotContain("foo");
    }

    @Test
    void indexesDigitsWithoutPunctuation() {
        assertThat(indexer.indexDigits("529.982.247-25"))
                .isEqualTo(indexer.indexDigits("52998224725"));
    }

    @Test
    void differentValuesProduceDifferentIndexes() {
        assertThat(indexer.index("a@example.com"))
                .isNotEqualTo(indexer.index("b@example.com"));
    }

    @Test
    void matchesInConstantTimeHelper() {
        String index = indexer.index("foo@bar.com");

        assertThat(indexer.matches("FOO@BAR.COM", index)).isTrue();
        assertThat(indexer.matches("other@bar.com", index)).isFalse();
    }

    @Test
    void returnsNullForEmptyValues() {
        assertThat(indexer.index(null)).isNull();
        assertThat(indexer.index(" ")).isNull();
        assertThat(indexer.indexDigits(null)).isNull();
    }
}
