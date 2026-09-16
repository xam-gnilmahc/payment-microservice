package com.payment.microservice.controller;

import com.payment.microservice.traits.ApiResponse;
import com.payment.microservice.dto.CreateUserCredentialsRequest;
import com.payment.microservice.model.UserCredentials;
import com.payment.microservice.repository.UserCredentialsRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController = This class handles HTTP requests and returns JSON
// @RequestMapping("/api/v1/user-credentials") = Base path for all endpoints
// @RequiredArgsConstructor = Auto-creates constructor for dependency injection
@RestController
@RequestMapping("/api/v1/user-credentials")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    // Injected by Spring - handles database operations for UserCredentials table
    private final UserCredentialsRepository repository;

    // @PostMapping = POST request to /api/v1/user-credentials (create new)
    // @Valid = Validates request body using annotations in CreateUserCredentialsRequest
    // @RequestBody = Converts JSON from request body to CreateUserCredentialsRequest object
    // THIS ENDPOINT NEEDS JWT TOKEN (protected by JwtFilter)
    @PostMapping
    public ResponseEntity<ApiResponse<UserCredentials>> create(@Valid @RequestBody CreateUserCredentialsRequest request) {
        // Build UserCredentials object from request
        UserCredentials credentials = UserCredentials.builder()
                .userId(request.getUserId())
                .gateway(request.getGateway())
                .gatewayName(request.getGatewayName())
                .publicKey(request.getPublicKey())
                .secretKey(request.getSecretKey())
                .webhookSecret(request.getWebhookSecret())
                .metadata(request.getMetadata())
                .isActive(true)
                .build();

        // Save to database and return response
        UserCredentials saved = repository.save(credentials);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Credentials created", 201, saved));
    }

    // @GetMapping = GET request to /api/v1/user-credentials (get all)
    // THIS ENDPOINT NEEDS JWT TOKEN
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserCredentials>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Success", 200, repository.findAll()));
    }

    // @GetMapping("/{id}") = GET request to /api/v1/user-credentials/1
    // @PathVariable = Takes value from URL path {id} and maps to Long id parameter
    // THIS ENDPOINT NEEDS JWT TOKEN
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserCredentials>> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(cred -> ResponseEntity.ok(ApiResponse.success("Success", 200, cred)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Credentials not found", 404)));
    }

    // @GetMapping("/user/{userId}") = GET request to /api/v1/user-credentials/user/1
    // @PathVariable = Takes value from URL path {userId} and maps to Integer userId
    // THIS ENDPOINT NEEDS JWT TOKEN
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserCredentials>>> getByUserId(@PathVariable Integer userId) {
        return ResponseEntity.ok(ApiResponse.success("Success", 200, repository.findByUserId(userId)));
    }
}
