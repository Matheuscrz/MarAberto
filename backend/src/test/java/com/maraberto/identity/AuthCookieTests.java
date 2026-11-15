package com.maraberto.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.identity.api.AuthCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * O nome do cookie depende do ambiente. Aceitar os dois nomes deixaria um
 * {@code maraberto_rt} plantado por outro host sombrear o cookie {@code __Secure-}.
 */
class AuthCookieTests {

    @Test
    void readsOnlyTheSecureNameInHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookie.INSECURE_NAME, "plantado"),
                new Cookie(AuthCookie.NAME, "verdadeiro"));

        assertThat(AuthCookie.read(request, true)).isEqualTo("verdadeiro");
    }

    @Test
    void ignoresUnsecureCookieInHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookie.INSECURE_NAME, "plantado"));

        assertThat(AuthCookie.read(request, true)).isNull();
    }

    @Test
    void readsOnlyTheDevNameWhenSecureIsOff() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookie.NAME, "de-outro-ambiente"),
                new Cookie(AuthCookie.INSECURE_NAME, "dev"));

        assertThat(AuthCookie.read(request, false)).isEqualTo("dev");
    }

    @Test
    void treatsBlankValueAsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookie.NAME, "  "));

        assertThat(AuthCookie.read(request, true)).isNull();
    }

    @Test
    void returnsNullWithoutCookies() {
        assertThat(AuthCookie.read(new MockHttpServletRequest(), true)).isNull();
    }
}
