package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.HashMap;
import java.util.Map;

/**
 * StripeService handles all Stripe-specific payment operations.
 *
 * <p>NOT a Spring bean — created manually by PaymentService.getService() with the merchant's secret
 * key. Payment logging is handled by WebhookController, not here.
 */
public class StripeService implements PaymentGatewayService {

  private final String secretKey;

  public StripeService(String secretKey) {
    this.secretKey = secretKey;
    // Set the Stripe API key so all Stripe calls use this merchant's account
    Stripe.apiKey = secretKey;
  }

  /**
   * Creates a PaymentIntent on Stripe.
   *
   * <p>Stores customerId, email, and name in PaymentIntent metadata so webhooks can retrieve them
   * later for logging. Returns clientSecret for frontend to confirm the payment.
   *
   * @param request contains customerId, amount, email, name, and billing info
   * @return map with paymentIntentId, clientSecret, status, and message
   */
  public Map<String, String> createPaymentIntent(PaymentRequest request) {
    try {
      // Step 1: Store user info in metadata so webhooks can retrieve it for logging
      // Webhooks don't have access to our database, so we pass data via Stripe metadata
      Map<String, String> metadata = new HashMap<>();
      metadata.put("customerId", String.valueOf(request.getCustomerId()));
      if (request.getEmail() != null) {
        metadata.put("email", request.getEmail());
      }
      if (request.getName() != null) {
        metadata.put("name", request.getName());
      }

      // Step 2: Build PaymentIntent params — Stripe charges in cents, so multiply by 100
      PaymentIntentCreateParams params =
          PaymentIntentCreateParams.builder()
              .setAmount(request.getAmount().longValue() * 100) // convert to cents
              .setCurrency("usd")
              .setCaptureMethod(
                  PaymentIntentCreateParams.CaptureMethod.AUTOMATIC) // Fixes automatic_async issue
              .setAutomaticPaymentMethods(
                  PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                      .setEnabled(true)
                      .setAllowRedirects(
                          PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.ALWAYS)
                      .build())
              .putAllMetadata(metadata)
              .build();

      // Step 3: Call Stripe API to create the PaymentIntent
      PaymentIntent paymentIntent = PaymentIntent.create(params);

      // Step 4: Return clientSecret so frontend can confirm the payment with Stripe.js
      Map<String, String> response = new HashMap<>();
      response.put("paymentIntentId", paymentIntent.getId());
      response.put("clientSecret", paymentIntent.getClientSecret());
      response.put("status", paymentIntent.getStatus());
      response.put("message", "PaymentIntent created - status: " + paymentIntent.getStatus());
      return response;

    } catch (Exception e) {
      // Stripe API error (card declined, invalid amount, etc.)
      throw new RuntimeException("Payment failed: " + e.getMessage());
    }
  }

  /**
   * Retrieves the current status of a PaymentIntent from Stripe.
   *
   * <p>Called by the frontend after confirming payment to check if it succeeded, requires 3DS
   * authentication, or failed. No logging here — webhooks handle that.
   *
   * @param paymentIntentId Stripe PaymentIntent ID (e.g., pi_xxx)
   * @param customerId internal user ID (used by PaymentService to resolve gateway)
   * @param paymentMethod "card", "apple_pay", or "google_pay"
   * @return map with paymentIntentId, status, clientSecret, amount, and message
   */
  public Map<String, String> getPaymentIntentStatus(
      String paymentIntentId, Long customerId, String paymentMethod) {
    try {
      // Step 1: Retrieve the PaymentIntent from Stripe to get current status
      PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

      // Step 2: Build response with status info for the frontend
      Map<String, String> response = new HashMap<>();
      response.put("paymentIntentId", paymentIntent.getId());
      response.put("status", paymentIntent.getStatus());
      response.put("clientSecret", paymentIntent.getClientSecret());
      response.put("amount", String.valueOf(paymentIntent.getAmount()));
      response.put("message", "PaymentIntent status: " + paymentIntent.getStatus());

      // Step 3: If succeeded, update message — frontend uses this to show success UI
      if ("succeeded".equals(paymentIntent.getStatus())) {
        response.put("transactionId", paymentIntent.getId());
        response.put(
            "chargeId",
            paymentIntent.getLatestCharge() != null ? paymentIntent.getLatestCharge() : "");
        response.put("message", "Payment successful - charge ID: " + response.get("chargeId"));
      }

      return response;

    } catch (Exception e) {
      // PaymentIntent not found or Stripe API error
      throw new RuntimeException("Failed to retrieve payment: " + e.getMessage());
    }
  }
}
