package com.payment.microservice.controller;

import com.payment.microservice.model.User;
import com.payment.microservice.repository.UserRepository;
import com.payment.microservice.traits.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
    public class UserListController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listUsers() {
        List<User> users = userRepository.findAll();

        List<Map<String, Object>> userList = users.stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "isActive", user.getIsActive()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Users fetched", 200, userList));
    }
}
