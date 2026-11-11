package com.maraberto.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Cadastro + login real de um operador para os testes de ponta a ponta.
 *
 * <p>Passa pelo {@link AuthenticationService} para que nome/e-mail/CPF sejam
 * cifrados e indexados de verdade, e depois pelo endpoint de login para obter um
 * JWT assinado.
 */
public final class TestOperatorAuth {

    private TestOperatorAuth() {
    }

    public record Session(UUID userId, String accessToken) {
    }

    public static Session registerAndLogin(
            AuthenticationService authenticationService,
            TestRestTemplate restTemplate,
            ObjectMapper objectMapper,
            String role) {
        String suffix = UUID.randomUUID().toString();
        String email = "operador-" + suffix + "@teste.dev";
        UUID userId = authenticationService.registerOperational(
                new RegisterOperationalUserCommand(
                        "Operador " + suffix.substring(0, 8),
                        "T-" + suffix.substring(0, 8),
                        email,
                        validCpf(),
                        "demo1234",
                        Role.valueOf(role)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("identifier", email, "password", "demo1234"), headers),
                String.class);
        if (response.getStatusCode().value() != 200) {
            throw new IllegalStateException("Login de teste falhou: " + response.getStatusCode());
        }
        try {
            return new Session(userId,
                    objectMapper.readTree(response.getBody()).get("accessToken").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Resposta de login sem accessToken", exception);
        }
    }

    private static String validCpf() {
        int[] digits = new int[11];
        for (int i = 0; i < 9; i++) {
            digits[i] = ThreadLocalRandom.current().nextInt(10);
        }
        digits[9] = checkDigit(digits, 9, 10);
        digits[10] = checkDigit(digits, 10, 11);
        StringBuilder builder = new StringBuilder(11);
        for (int digit : digits) {
            builder.append(digit);
        }
        return builder.toString();
    }

    private static int checkDigit(int[] digits, int length, int weight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * (weight - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
