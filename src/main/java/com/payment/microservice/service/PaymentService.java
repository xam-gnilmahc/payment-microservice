package com.payment.microservice.service;

import com.payment.microservice.model.PaymentGateway;
import com.payment.microservice.model.UserPaymentCredentials;
import com.payment.microservice.model.UserPaymentGateway;
import com.payment.microservice.repository.PaymentGatewayRepository;
import com.payment.microservice.repository.UserPaymentCredentialsRepository;
import com.payment.microservice.repository.UserPaymentGatewayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PaymentService is a factory that resolves which payment gateway a user has enabled and creates
 * the matching gateway service (e.g., StripeService, future PayPalService, etc.).
 *
 * <p>Flow: 1. Find user's enabled gateway (UserPaymentGateway) 2. Look up gateway title (STRIPE,
 * PAYPAL, etc.) 3. Get the user's credentials for that gateway 4. Create and return the correct
 * service via switch/case
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentGatewayRepository paymentGatewayRepository;
  private final UserPaymentGatewayRepository userPaymentGatewayRepository;
  private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;

  /**
   * Resolves the gateway for a given customer and returns an initialized PaymentGatewayService.
   *
   * @param customerId internal user ID
   * @return an initialized gateway service ready to process payments
   * @throws RuntimeException if no enabled gateway or credentials found
   */
  public PaymentGatewayService getService(Long customerId) {
    // Step 1: Find which gateway this user has enabled (e.g., Stripe, PayPal)
    // Each user can have multiple gateways assigned but only one enabled at a time
    UserPaymentGateway upg =
        userPaymentGatewayRepository.findByUserIdAndEnabled(customerId, true).stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No enabled gateway found for user"));

    // Step 2: Look up the gateway name from the payment_gateways table
    // This tells us which service to instantiate (stripe, paypal, etc.)
    String gatewayTitle =
        paymentGatewayRepository
            .findById(upg.getPaymentGatewayId())
            .map(PaymentGateway::getTitle)
            .orElse("UNKNOWN");

    // Step 3: Get the merchant's API credentials for this gateway
    // Stored in user_payment_credentials table (e.g., Stripe secret key)
    UserPaymentCredentials credentials =
        userPaymentCredentialsRepository.findByUserPaymentGatewaysId(upg.getId()).stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No credentials found"));

    // Step 4: Create the matching gateway service with the merchant's credentials
    // Each gateway service handles its own API calls (Stripe API, PayPal API, etc.)
    return switch (gatewayTitle.toLowerCase()) {
      case "stripe" -> new StripeService(credentials.getSecretKey());
        // case "paypal" -> new PayPalService(credentials.getClientId(), credentials.getSecret());
      default ->
          throw new RuntimeException("Gateway '" + gatewayTitle + "' is not configured yet.");
    };
  }
}
