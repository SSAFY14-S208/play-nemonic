package com.nemonicworld.global.config;

import com.nemonicworld.common.jwt.AdminJwtAuthenticationFilter;
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

    private final AdminJwtAuthenticationFilter adminJwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;

    public SecurityConfig(AdminJwtAuthenticationFilter adminJwtAuthenticationFilter,
        JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint) {
        this.adminJwtAuthenticationFilter = adminJwtAuthenticationFilter;
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(jsonAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/login", "/api/v1/auth/reissue")
                .permitAll().requestMatchers("/api/v1/auth/logout", "/api/v1/admins", "/api/v1/admins/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/admin/inquiries", "/api/v1/admin/inquiries/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/backoffice/gms/prompts", "/api/v1/backoffice/gms/prompts/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/backoffice/system-parameters", "/api/v1/backoffice/system-parameters/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/backoffice/relay-rooms", "/api/v1/backoffice/relay-rooms/**")
                .hasAnyRole("ADMIN", "SUPER_ADMIN").anyRequest().permitAll())
            .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
