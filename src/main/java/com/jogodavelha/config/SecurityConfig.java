package com.jogodavelha.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ROUTES = {
            "/auth/**", "/api/health", "/api/health/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/ws/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   ObjectMapper objectMapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                    new JwtAuthenticationEntryPoint(objectMapper)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ROUTES).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
