package com.maraberto.identity.infra.security;

import com.maraberto.shared.security.web.ApiAccessDeniedHandler;
import com.maraberto.shared.security.web.ApiAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Regras de autorização por área e por papel (ADR-009).
 *
 * <p>A sessão é stateless: o access token vai no header e o refresh no cookie
 * {@code Path=/api/v1/auth; SameSite=Strict}, o que dispensa CSRF para as rotas
 * de sessão. A autorização real acontece aqui e no escopo de cada serviço — a
 * guarda de rota do frontend é só experiência de uso.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Utilitários e superfícies públicas.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Sessão: login/refresh/logout são públicos (o cookie é a prova);
                        // /me exige access token válido.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        // Rotas públicas do banhista.
                        .requestMatchers(HttpMethod.POST, "/api/v1/emergency-calls/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/beaches/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tides/**").permitAll()
                        // Ocupação é anônima por definição (RF10/RNF04): amostra e leitura
                        // públicas, sem sessão.
                        .requestMatchers("/api/v1/occupancy/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Áreas operacionais por papel.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/management/**")
                        .hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers("/api/v1/operations/**").hasAnyRole("COORDINATOR", "ADMIN")
                        .requestMatchers("/api/v1/dispatch/**")
                        .hasAnyRole("LIFEGUARD", "COORDINATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
