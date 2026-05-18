package com.nemonicworld.global.config;

import com.nemonicworld.common.jwt.AdminJwtAuthenticationFilter;
import com.nemonicworld.common.jwt.JsonAccessDeniedHandler;
import com.nemonicworld.common.jwt.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ROLE_VIEWER = "VIEWER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminJwtAuthenticationFilter adminJwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;

    public SecurityConfig(AdminJwtAuthenticationFilter adminJwtAuthenticationFilter,
        JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint, JsonAccessDeniedHandler jsonAccessDeniedHandler) {
        this.adminJwtAuthenticationFilter = adminJwtAuthenticationFilter;
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
        this.jsonAccessDeniedHandler = jsonAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jsonAuthenticationEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/login", "/api/v1/auth/reissue")
                .permitAll().requestMatchers("/api/v1/auth/logout", "/api/v1/admins", "/api/v1/admins/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/admin/inquiries", "/api/v1/admin/inquiries/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/admin/community/memos", "/api/v1/admin/community/memos/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/admin/logs", "/api/v1/admin/logs/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/admin/metrics", "/api/v1/admin/metrics/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/backoffice/gms/prompts", "/api/v1/backoffice/gms/prompts/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/backoffice/system-parameters", "/api/v1/backoffice/system-parameters/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/backoffice/relay-rooms", "/api/v1/backoffice/relay-rooms/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/backoffice/flipbook-rooms", "/api/v1/backoffice/flipbook-rooms/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN)
                .requestMatchers("/api/v1/backoffice/infinite-canvas/canvases",
                    "/api/v1/backoffice/infinite-canvas/canvases/**")
                .hasAnyRole(ROLE_VIEWER, ROLE_ADMIN, ROLE_SUPER_ADMIN).anyRequest().permitAll())
            .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
