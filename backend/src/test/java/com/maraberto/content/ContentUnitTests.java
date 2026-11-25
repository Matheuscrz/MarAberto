package com.maraberto.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maraberto.content.api.ContentApiExceptionHandler;
import com.maraberto.content.api.ContentChanged;
import com.maraberto.content.api.ContentKind;
import com.maraberto.content.api.CreateContentRequest;
import com.maraberto.content.application.PublicContentCache;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ContentUnitTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void sanitizedAndValidationRejectMalformedUrlAndInvalidYoutubeId() throws Exception {
        // @Sanitized falha fechado para "" em políticas estritas; Bean Validation rejeita "".
        CreateContentRequest badYoutube = objectMapper.readValue("""
                {
                  "kind": "VIDEO",
                  "title": "Teste",
                  "description": "Desc",
                  "category": "Geral",
                  "youtubeId": "invalido"
                }
                """, CreateContentRequest.class);
        assertThat(badYoutube.youtubeId()).isEmpty();
        Set<ConstraintViolation<CreateContentRequest>> youtubeViolations = validator.validate(badYoutube);
        assertThat(youtubeViolations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("youtubeId"));

        CreateContentRequest badUrl = objectMapper.readValue("""
                {
                  "kind": "CAMPAIGN",
                  "title": "Teste",
                  "description": "Desc",
                  "category": "Geral",
                  "externalUrl": "javascript:alert(1)"
                }
                """, CreateContentRequest.class);
        assertThat(badUrl.externalUrl()).isEmpty();
        Set<ConstraintViolation<CreateContentRequest>> urlViolations = validator.validate(badUrl);
        assertThat(urlViolations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("externalUrl"));

        // Quando omitidos (null), ambos são válidos para tipos opcionais.
        CreateContentRequest optionalOmitted = objectMapper.readValue("""
                {
                  "kind": "GUIDE",
                  "title": "Guia sem link",
                  "description": "Desc",
                  "category": "Geral"
                }
                """, CreateContentRequest.class);
        assertThat(validator.validate(optionalOmitted)).isEmpty();
    }

    @Test
    void exceptionHandlerReturnsRfc9457ProblemJson() throws Exception {
        ContentApiExceptionHandler handler = new ContentApiExceptionHandler();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON inválido",
                new IllegalArgumentException("URL deve usar http ou https"),
                (HttpInputMessage) null);

        ResponseEntity<Object> response = handler.handleException(
                ex,
                new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/management/content")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getDetail()).isEqualTo("URL deve usar http ou https");
    }

    @Test
    void sanitizedAcceptsValidYoutubeIdAndHttpsUrl() throws Exception {
        CreateContentRequest request = objectMapper.readValue("""
                {
                  "kind": "VIDEO",
                  "title": "  <b>Correntes</b> de Retorno  ",
                  "description": "Aprenda a identificar",
                  "category": "Prevenção",
                  "youtubeId": "dQw4w9WgXcQ",
                  "externalUrl": "https://maraberto.org/guia",
                  "published": true
                }
                """, CreateContentRequest.class);

        assertThat(request.kind()).isEqualTo(ContentKind.VIDEO);
        assertThat(request.title()).isEqualTo("Correntes de Retorno");
        assertThat(request.youtubeId()).isEqualTo("dQw4w9WgXcQ");
        assertThat(request.externalUrl()).isEqualTo("https://maraberto.org/guia");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void publicContentCacheInvalidatesOnContentChangedEvent() {
        PublicContentCache cache = new PublicContentCache(Duration.ofSeconds(30));
        AtomicInteger loads = new AtomicInteger();

        String first = cache.get("public:ALL", () -> "v" + loads.incrementAndGet());
        String cached = cache.get("public:ALL", () -> "v" + loads.incrementAndGet());
        assertThat(first).isEqualTo("v1");
        assertThat(cached).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);

        cache.onContentChanged(new ContentChanged(UUID.randomUUID(), "UPDATED"));

        String afterInvalidation = cache.get("public:ALL", () -> "v" + loads.incrementAndGet());
        assertThat(afterInvalidation).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(2);
    }
}
