package com.maraberto.identity.infra.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // saltLength: 16 bytes, hashLength: 32 bytes, parallelism: 1, memory: 65536 KB, iterations: 3
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }
}