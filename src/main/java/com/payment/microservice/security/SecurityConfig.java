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
   * SecurityContextRepository = how the Authentication is read from and written to the session.
   * AuthController injects this to save the login itself, because this project does not use
   * Spring Security's own login filter.
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * SecurityFilterChain = the main Spring Security configuration. 
   * It defines which endpoints are open to everyone, which require authentication, and which require specific roles. 
   * It also disables CSRF, sets session management, and configures exception handling to return JSON 401 responses instead of redirects.
   * 
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {

    http.csrf(csrf -> csrf.disable())
        // session management: create a session if required, otherwise don't create one
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        // use the custom SecurityContextRepository to read/write the Authentication from/to the session
        .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
        // answer with JSON 401 rather than Spring's default redirect to an HTML login page
        .exceptionHandling(exception -> exception.authenticationEntryPoint(customAuthEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Stripe calls this server to server. There is no browser session to check, and
                    // the signature is verified in WebhookController.
                    .requestMatchers("/api/v1/webhooks/**")
                    .permitAll()
                    // Spring Boot forwards to /error when a request fails, e.g. a Stripe webhook with
                    // no signature. Without this the error page itself needs a login, so the 401
                    // would overwrite the real status and Stripe would retry forever.
                    .requestMatchers("/error")
                    .permitAll()
                    // login, register, me and logout. The controller does the actual checking.
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    // Open to everyone: the HTML pages and static assets. The pages hold no data,
                    // they call the API instead, and a signed out visitor gets a 401 from that API
                    // and is shown the login page.
                    .requestMatchers(
                        "/", "/index.html", "/payment.html", "/pay",
                        "/stripe/**", "/authorize/**", "/admin/**",
                        "/css/**", "/common/**", "/img/**", "/images/**", "/assets/**",
                        "/favicon.ico")
                    .permitAll()
                    // admin APIs: the role is checked here, the controller does not do it
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    // everything else must be signed in
                    .anyRequest()
                    .authenticated())
        // Without these two, Spring Security adds its own login page generating filter and sends a
        // 302 to /login, which the frontend cannot read. The frontend needs the 401 body instead.
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());

    return http.build();
  }
}