package com.maraberto.identity.api;

import com.maraberto.identity.domain.AccountInactiveException;
import com.maraberto.identity.domain.DuplicateOperationalUserException;
import com.maraberto.identity.domain.InvalidCredentialsException;
import com.maraberto.identity.domain.InvalidRefreshTokenException;
import com.maraberto.identity.domain.OriginNotAllowedException;
import com.maraberto.identity.domain.SelfAdminProtectionException;
import com.maraberto.identity.domain.TemporaryPasswordExpiredException;
import com.maraberto.identity.domain.TooManyLoginAttemptsException;
import com.maraberto.identity.domain.UserNotFoundException;
import com.maraberto.identity.domain.WeakPasswordException;
import com.maraberto.shared.api.FieldValidationError;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros do módulo de identidade em RFC 9457.
 *
 * <p>Os tipos são URIs estáveis (identificadores, não redirecionamentos). Nenhum
 * detalhe revela se um e-mail/CPF existe: credencial inexistente e senha errada
 * compartilham {@code invalid-credentials}.
 */
@RestControllerAdvice(basePackageClasses = AuthController.class)
public class IdentityApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String INVALID_REQUEST = "https://maraberto.org/problems/invalid-request";
    public static final String MALFORMED_JSON = "https://maraberto.org/problems/malformed-json";
    public static final String INVALID_CREDENTIALS = "https://maraberto.org/problems/invalid-credentials";
    public static final String ACCOUNT_INACTIVE = "https://maraberto.org/problems/account-inactive";
    public static final String INVALID_REFRESH_TOKEN = "https://maraberto.org/problems/invalid-refresh-token";
    public static final String DUPLICATE_USER = "https://maraberto.org/problems/duplicate-user";
    public static final String ORIGIN_NOT_ALLOWED = "https://maraberto.org/problems/origin-not-allowed";
    public static final String TOO_MANY_ATTEMPTS = "https://maraberto.org/problems/too-many-login-attempts";
    public static final String PASSWORD_CHANGE_REQUIRED =
            "https://maraberto.org/problems/password-change-required";
    public static final String TEMPORARY_PASSWORD_EXPIRED =
            "https://maraberto.org/problems/temporary-password-expired";
    public static final String WEAK_PASSWORD = "https://maraberto.org/problems/weak-password";
    public static final String USER_NOT_FOUND = "https://maraberto.org/problems/user-not-found";
    public static final String SELF_ADMIN_PROTECTION =
            "https://maraberto.org/problems/self-admin-protection";

    private static final MediaType PROBLEM_JSON_UTF8 =
            new MediaType(MediaType.APPLICATION_PROBLEM_JSON, StandardCharsets.UTF_8);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                INVALID_REQUEST,
                "Requisição inválida",
                "Um ou mais campos não atendem às regras de validação.",
                requestUri(request));
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList());
        return response(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                MALFORMED_JSON,
                "JSON inválido",
                "O corpo da requisição não pôde ser interpretado.",
                requestUri(request));
        return response(problem, headers);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Object> handleInvalidCredentials(
            InvalidCredentialsException exception, WebRequest request) {
        return response(problem(
                HttpStatus.UNAUTHORIZED,
                INVALID_CREDENTIALS,
                "Credenciais inválidas",
                "Identificador ou senha incorretos.",
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(AccountInactiveException.class)
    ResponseEntity<Object> handleAccountInactive(
            AccountInactiveException exception, WebRequest request) {
        return response(problem(
                HttpStatus.FORBIDDEN,
                ACCOUNT_INACTIVE,
                "Conta inativa",
                "Este operador está desativado. Procure a coordenação ou a TI.",
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<Object> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception, WebRequest request) {
        return response(problem(
                HttpStatus.UNAUTHORIZED,
                INVALID_REFRESH_TOKEN,
                "Sessão inválida",
                "A sessão expirou ou foi encerrada. Entre novamente.",
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(DuplicateOperationalUserException.class)
    ResponseEntity<Object> handleDuplicateUser(
            DuplicateOperationalUserException exception, WebRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                DUPLICATE_USER,
                "Cadastro duplicado",
                exception.getMessage(),
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(OriginNotAllowedException.class)
    ResponseEntity<Object> handleOriginNotAllowed(
            OriginNotAllowedException exception, WebRequest request) {
        return response(problem(
                HttpStatus.FORBIDDEN,
                ORIGIN_NOT_ALLOWED,
                "Origem não permitida",
                "Esta origem não pode executar operações de sessão.",
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    ResponseEntity<Object> handleTooManyAttempts(
            TooManyLoginAttemptsException exception, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        return response(problem(
                HttpStatus.TOO_MANY_REQUESTS,
                TOO_MANY_ATTEMPTS,
                "Muitas tentativas",
                "Aguarde " + exception.retryAfterSeconds()
                        + " segundo(s) antes de tentar entrar novamente.",
                requestUri(request)), headers);
    }

    @ExceptionHandler(TemporaryPasswordExpiredException.class)
    ResponseEntity<Object> handleTemporaryPasswordExpired(
            TemporaryPasswordExpiredException exception, WebRequest request) {
        return response(problem(
                HttpStatus.FORBIDDEN,
                TEMPORARY_PASSWORD_EXPIRED,
                "Senha temporária expirada",
                exception.getMessage(),
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(WeakPasswordException.class)
    ResponseEntity<Object> handleWeakPassword(
            WeakPasswordException exception, WebRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                WEAK_PASSWORD,
                "Senha não atende à política",
                exception.getMessage(),
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<Object> handleUserNotFound(
            UserNotFoundException exception, WebRequest request) {
        return response(problem(
                HttpStatus.NOT_FOUND,
                USER_NOT_FOUND,
                "Operador não encontrado",
                "O operador informado não existe.",
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(SelfAdminProtectionException.class)
    ResponseEntity<Object> handleSelfAdminProtection(
            SelfAdminProtectionException exception, WebRequest request) {
        return response(problem(
                HttpStatus.CONFLICT,
                SELF_ADMIN_PROTECTION,
                "Acesso de TI preservado",
                exception.getMessage(),
                requestUri(request)), new HttpHeaders());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> handleIllegalArgument(
            IllegalArgumentException exception, WebRequest request) {
        return response(problem(
                HttpStatus.BAD_REQUEST,
                INVALID_REQUEST,
                "Requisição inválida",
                exception.getMessage(),
                requestUri(request)), new HttpHeaders());
    }

    private ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(PROBLEM_JSON_UTF8)
                .body(problem);
    }

    private String requestUri(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private ProblemDetail problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            String requestUri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(requestUri));
        return problem;
    }
}
