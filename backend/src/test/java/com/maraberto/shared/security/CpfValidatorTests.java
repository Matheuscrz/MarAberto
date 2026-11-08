package com.maraberto.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfValidatorTests {

    @Test
    void acceptsValidCpf() {
        assertThat(CpfValidator.isValid("529.982.247-25")).isTrue();
        assertThat(CpfValidator.normalize("529.982.247-25")).isEqualTo("52998224725");
    }

    @Test
    void rejectsInvalidCpf() {
        assertThat(CpfValidator.isValid("529.982.247-24")).isFalse();
        assertThat(CpfValidator.isValid("111.111.111-11")).isFalse();
        assertThat(CpfValidator.isValid("123")).isFalse();
        assertThat(CpfValidator.isValid(null)).isFalse();
    }
}
