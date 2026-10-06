package com.payment.microservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

// @Configuration = this class is picked up at startup and its @Bean methods become singletons
// @EnableWebSecurity = turns on Spring Security's filter chain
// @RequiredArgsConstructor = Spring injects the final fields below
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomAuthEntryPoint customAuthEntryPoint;

  // BCrypt hashes the password on register and compares it on login
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * UserDetailsService = how Spring Security looks up a user by email. It returns the User entity, which implements UserDetails, or throws UsernameNotFoundException if not found.
   */
  @Bean
  public UserDetailsService userDetailsService(
      com.payment.microservice.repository.UserRepository userRepository) {
    return email ->
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
  }

  /**
   * AuthenticationManager = how Spring Security checks the password. It uses the UserDetailsService and PasswordEncoder to verify the credentials. This bean is needed for manual authentication in the AuthController.
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  /**
   * SecurityContextRepository = how Spring Security stores the Authentication in the session. The default is HttpSessionSecurityContextRepository, which keeps the session alive across requests. This bean is needed for manual login/logout in the AuthController.
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
  
  /**
   * SecurityFilterChain = the main configuration for Spring Security. It defines which endpoints are open, which require authentication, how sessions are managed, and how exceptions are handled. It also disables CSRF and form login, since this is a REST API.
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
    http.csrf(csrf -> csrf.disable())
        // session management: create a session if required, and allow only one session per user
        .sessionManagement(
            session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .maximumSessions(1))
        .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
        // exception handling: use the custom entry point to return 401 Unauthorized for unauthenticated requests
        .exceptionHandling(exception -> exception.authenticationEntryPoint(customAuthEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/webhooks/**")
                    .permitAll()
                    // Spring Boot forwards to /error when a request fails, e.g. a Stripe webhook
                    // with no signature header. Without this the error page itself needs a login,
                    // so the 401 would overwrite the real status and Stripe would retry forever.
                    .requestMatchers("/error")
                    .permitAll()
                    // login, register, me and logout are open, the controller does the checking
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/payment.html")
                    .permitAll()
                    .requestMatchers("/", "/index.html")
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
                    // static assets. Without these the logo and favicon return 401, because they
                    // fall through to anyRequest().authenticated() below.
                    .requestMatchers("/img/**", "/images/**", "/assets/**", "/favicon.ico")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .permitAll()
                    // only admins can access /api/v1/admin/**
                    // the controller does not check the role, Spring Security does it here
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    // all other requests require authentication
                    .anyRequest()
                    .authenticated())
        // disable form login and basic auth, since this is a REST API
        .formLogin(form -> form.disable())
        // disable HTTP Basic auth, since this is a REST API
        .httpBasic(basic -> basic.disable());

    return http.build();
  }
}
