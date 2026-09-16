package com.payment.microservice.controller;

import com.payment.microservice.dto.CreateBillingAddressRequest;
import com.payment.microservice.model.BillingAddress;
import com.payment.microservice.model.User;
import com.payment.microservice.repository.BillingAddressRepository;
import com.payment.microservice.traits.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing-addresses")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class BillingAddressController {

    private final BillingAddressRepository billingAddressRepository;

    private Long getUserId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BillingAddress>> create(@Valid @RequestBody CreateBillingAddressRequest request) {
        Long userId = getUserId();

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            billingAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                    .forEach(addr -> {
                        addr.setIsDefault(false);
                        billingAddressRepository.save(addr);
                    });
        }

        BillingAddress address = BillingAddress.builder()
                .userId(userId)
                .name(request.getName())
                .email(request.getEmail())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        billingAddressRepository.save(address);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Billing address created", 201, address));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BillingAddress>>> list() {
        Long userId = getUserId();
        List<BillingAddress> addresses = billingAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        return ResponseEntity.ok(ApiResponse.success("Billing addresses fetched", 200, addresses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BillingAddress>> get(@PathVariable Long id) {
        Long userId = getUserId();
        BillingAddress address = billingAddressRepository.findById(id)
                .filter(a -> a.getUserId().equals(userId))
                .orElse(null);
        if (address == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Address not found", 404));
        }
        return ResponseEntity.ok(ApiResponse.success("Address fetched", 200, address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long userId = getUserId();
        BillingAddress address = billingAddressRepository.findById(id)
                .filter(a -> a.getUserId().equals(userId))
                .orElse(null);
        if (address == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Address not found", 404));
        }
        billingAddressRepository.delete(address);
        return ResponseEntity.ok(ApiResponse.success("Address deleted", 200, null));
    }
}
