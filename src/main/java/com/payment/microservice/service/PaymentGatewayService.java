package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import java.util.Map;

/**
 * PaymentGatewayService - Interface for all payment gateways.
 *
 * <p>Each gateway (Stripe, AuthorizeNet, etc.) implements this. PaymentService.getService() returns
 * an initialized instance.
 */
public interface PaymentGatewayService {

  /**
   * Creates a payment intent using the gateway's API. Returns clientSecret for frontend
   * confirmation.
   */
  Map<String, String> createPaymentIntent(PaymentRequest request);

  /**
   * Confirms a payment by checking status with the gateway. Returns final status and details
   * (chargeId, cardLast4, etc.)
   */
  Map<String, String> getPaymentIntentStatus(
      String paymentIntentId, Long customerId, String paymentMethod);

  /** Refunds a payment by charge ID. Full refund only. */
  Map<String, String> refundPayment(String chargeId, Long customerId, String reason);
}
