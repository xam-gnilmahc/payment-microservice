package com.payment.microservice.security;

import com.payment.microservice.middleware.JwtAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// WHAT IS THIS FILE?
// This is the SECURITY CONFIGURATION - defines rules for which endpoints need token
//
// HOW DOES IT WORK?
// 1. This file runs ONCE when app starts (not on every request)
// 2. It CONFIGURES the security rules
// 3. It ADDS JwtAuth middleware to the filter chain
// 4. Spring Security framework USES these rules on every request
//
// THE COMPLETE FLOW:
// App starts → SecurityConfig runs → Configures rules → Adds JwtAuth to chain
//
// On each request:
// Request → JwtAuth runs (checks token) → SecurityConfig rules checked (automatic) → Controller
//
// WHO CHECKS THE RULES?
// Spring Security framework checks the rules AUTOMATICALLY after JwtAuth runs
// You don't write the check code - Spring does it based on your configuration
//
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Inject JwtAuth middleware (created by @Component in JwtAuth.java)
    private final JwtAuth jwtAuth;
    // Inject custom 403 error handler
    private final CustomAuthEntryPoint customAuthEntryPoint;

    // @Bean = Creates PasswordEncoder object at app startup
    // Used to encrypt/decrypt passwords (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // @Bean = Creates SecurityFilterChain at app startup
    // This CONFIGURES all security rules (runs ONCE, not on every request)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - not needed for REST APIs with JWT
            .csrf(csrf -> csrf.disable())

            // STATELESS - no session stored on server (JWT handles everything)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Custom 403 handler - returns ApiResponse format when token is invalid
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthEntryPoint)
            )

            // SECURITY RULES (Spring Security checks these AUTOMATICALLY on every request):
            .authorizeHttpRequests(auth -> auth
                // These endpoints are PUBLIC (no token needed):
                .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/payment.html", "/pay", "/css/**", "/js/**").permitAll()
                // All other endpoints NEED VALID JWT TOKEN:
                .anyRequest().authenticated()
            )

            // ADD JWT MIDDLEWARE TO FILTER CHAIN:
            // "Run JwtAuth BEFORE Spring's default authentication filter"
            // This means JwtAuth runs first on every request
            .addFilterBefore(jwtAuth, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
