package com.payment.microservice.security;

import com.payment.microservice.middleware.JwtAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuth jwtAuth;
  private final CustomAuthEntryPoint customAuthEntryPoint;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception.authenticationEntryPoint(customAuthEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/webhooks/**")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/payment.html")
                    .permitAll()
                    .requestMatchers("/index.html")
                    .permitAll()
                    .requestMatchers("/stripe/**")
                    .permitAll()
                    .requestMatchers("/authorize/**")
                    .permitAll()
                    .requestMatchers("/pay")
                    .permitAll()
                    .requestMatchers("/css/**")
                    .permitAll()
                    .requestMatchers("/common/**")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuth, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
