package com.payment.microservice.middleware;

import com.payment.microservice.model.User;
import com.payment.microservice.repository.UserRepository;
import com.payment.microservice.token.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

// WHAT IS THIS FILE?
// This is the MIDDLEWARE that runs for EVERY request before reaching controller
// It checks: "Is there a valid JWT token in the request?"
//
// HOW DOES IT WORK?
// 1. Request comes in (e.g., GET /api/v1/user-credentials)
// 2. This filter runs FIRST (before controller)
// 3. It looks for "Authorization: Bearer eyJhbG..." header
// 4. If token exists and is valid → marks user as "logged in"
// 5. If token missing or invalid → does nothing (SecurityConfig handles the 403)
//
// HOW DOES SECURITYCONFIG USE THIS?
// SecurityConfig adds this filter to the chain:
//   .addFilterBefore(jwtAuth, UsernamePasswordAuthenticationFilter.class);
// This means: "Run JwtAuth BEFORE Spring's default authentication filter"
//
@Component
@RequiredArgsConstructor
public class JwtAuth extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;             // validates JWT tokens
    private final UserRepository userRepository; // finds user in database

    // This method runs for EVERY HTTP request
    // Request = what client sends (URL, headers, body)
    // Response = what we send back
    // FilterChain = next filter in line (or controller if last filter)
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // STEP 1: Get "Authorization" header from request
        // Example header: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
        String header = request.getHeader("Authorization");

        // STEP 2: Check if header exists and starts with "Bearer "
        if (header != null && header.startsWith("Bearer ")) {

            // STEP 3: Extract token (remove "Bearer " prefix)
            // "Bearer eyJhbG..." → "eyJhbG..."
            String token = header.substring(7);

            // STEP 4: Validate token (check expiry, signature)
            if (jwtUtil.validateToken(token)) {

                // STEP 5: Extract email from token
                String email = jwtUtil.extractEmail(token);

                // STEP 6: Find user in database
                User user = userRepository.findByEmail(email).orElse(null);

                // STEP 7: If user exists and is active, mark as "logged in"
                if (user != null && user.getIsActive()) {

                    // Create authentication token (marks user as authenticated)
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, new ArrayList<>());

                    // Store in SecurityContext (Spring Security remembers this user is logged in)
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        // STEP 8: Continue to next filter or controller
        // If token was valid → SecurityContext has user → controller runs
        // If token was invalid/missing → SecurityContext is empty → SecurityConfig checks rules
        filterChain.doFilter(request, response);
    }
}
