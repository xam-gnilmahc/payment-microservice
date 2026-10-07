package com.payment.microservice.controller;

import com.payment.microservice.dto.CreateBillingAddressRequest;
import com.payment.microservice.model.BillingAddress;
import com.payment.microservice.model.User;
import com.payment.microservice.repository.BillingAddressRepository;
import com.payment.microservice.traits.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.payment.microservice.traits.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing-addresses")
@RequiredArgsConstructor
public class BillingAddressController {

  private final BillingAddressRepository billingAddressRepository;
  
  /**
   * Create a new billing address for the current user. 
   * If the request specifies that this address should be the default, any existing default addresses for the user will be unset. Returns 201 Created with the new address, or 400 Bad Request if validation fails.
   */
  @PostMapping
  public ResponseEntity<ApiResponse<BillingAddress>> create(
      @Valid @RequestBody CreateBillingAddressRequest request) {
    Long userId = CurrentUser.id();

    if (Boolean.TRUE.equals(request.getIsDefault())) {
      billingAddressRepository
          .findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
          .forEach(
              addr -> {
                addr.setIsDefault(false);
                billingAddressRepository.save(addr);
              });
    }

    BillingAddress address =
        BillingAddress.builder()
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
  
  /**
   * List all billing addresses for the current user, ordered by default status and creation date. Returns 200 OK with the list of addresses.
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<BillingAddress>>> list() {
    Long userId = CurrentUser.id();
    List<BillingAddress> addresses =
        billingAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    return ResponseEntity.ok(ApiResponse.success("Billing addresses fetched", 200, addresses));
  }
  
  /**
   * Delete a billing address by ID for the current user. If the address does not exist or does not belong to the user, returns 400 Bad Request. Otherwise, deletes the address and returns 200 OK.
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    Long userId = CurrentUser.id();
    BillingAddress address =
        billingAddressRepository
            .findById(id)
            .filter(a -> a.getUserId().equals(userId))
            .orElse(null);
    if (address == null) {
      return ResponseEntity.badRequest().body(ApiResponse.error("Address not found", 404));
    }
    billingAddressRepository.delete(address);
    return ResponseEntity.ok(ApiResponse.success("Address deleted", 200, null));
  }
}
