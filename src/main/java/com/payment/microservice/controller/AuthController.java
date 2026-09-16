package com.payment.microservice.controller;

import com.payment.microservice.token.JwtUtil;
import com.payment.microservice.traits.ApiResponse;
import com.payment.microservice.dto.LoginRequest;
import com.payment.microservice.dto.RegisterRequest;
import com.payment.microservice.model.User;
import com.payment.microservice.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

// @RestController = This class handles HTTP requests and returns JSON responses
// @RequestMapping("/api/v1/auth") = All endpoints in this class start with /api/v1/auth
// @RequiredArgsConstructor = Auto-creates constructor for all final fields (dependency injection)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // These are injected automatically by Spring (dependency injection)
    private final UserRepository userRepository;  // database operations for User table
    private final JwtUtil jwtUtil;                // JWT token generate/validate
    private final PasswordEncoder passwordEncoder; // encrypt/decrypt passwords

    // @PostMapping("/register") = POST request to /api/v1/auth/register
    // @Valid = Validates the request body using annotations in RegisterRequest
    // @RequestBody = Reads JSON from request body and converts to RegisterRequest object
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(@Valid @RequestBody RegisterRequest request) {
        // Check if email already exists in database
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Email already exists", 400));
        }

        // Create new user with hashed password
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt encryption
                .isActive(true)
                .build();

        // Save user to database
        userRepository.save(user);

        // Generate JWT token with email and userId
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getName());

        // Return 201 Created with token
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", 201,
                        new TokenResponse(token, user.getEmail(), user.getId(), user.getName())));
    }

    // @PostMapping("/login") = POST request to /api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(@Valid @RequestBody LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        // Check if user exists and password matches
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid email or password", 400));
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getName());

        // Return 200 OK with token
        return ResponseEntity.ok(ApiResponse.success("Login successful", 200,
                new TokenResponse(token, user.getEmail(), user.getId(), user.getName())));
    }

    // record = shortcut for creating a class with fields, getters, equals, hashCode, toString
    // This defines the structure of the response data
    public record TokenResponse(String token, String email, Long userId, String name) {}
}
