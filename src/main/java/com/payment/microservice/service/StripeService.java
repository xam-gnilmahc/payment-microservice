package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.model.PaymentEvent;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
import com.payment.microservice.repository.PaymentLogRepository;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * StripeService - Handles all Stripe payment operations and logging.
 *
 * This service is responsible for:
 * 1. Creating PaymentIntents on Stripe
 * 2. Checking PaymentIntent status after frontend confirmation
 * 3. Logging all payment events to payment_logs table
 *
 * Logging is done here (not in PaymentService) because Stripe is the only
 * gateway that actually processes payments. Other gateways just return pending.
 */
@Service
@RequiredArgsConstructor
public class StripeService {

    private final PaymentLogRepository paymentLogRepository;

    /**
     * Finds a payment log by transaction ID.
     *
     * @param transactionId The Stripe PaymentIntent ID (e.g., "pi_xxx")
     * @return PaymentLog if found, null otherwise
     */
    public PaymentLog findLogByTransactionId(String transactionId) {
        return paymentLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(l -> transactionId.equals(l.getTransactionId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a PaymentIntent on Stripe and logs it to payment_logs.
     *
     * Flow:
     * 1. Logs PAYMENT_INTENT event with status INITIATED
     * 2. Calls Stripe API to create PaymentIntent
     * 3. On success: updates log with PaymentIntent ID
     * 4. On failure: updates log with status FAILED and error message
     *
     * @param request   Payment request containing amount, customer info
     * @param secretKey Stripe secret key for this merchant
     * @return Map with paymentIntentId, clientSecret, status, message
     */
    public Map<String, String> createPaymentIntent(PaymentRequest request, String secretKey) {
        Stripe.apiKey = secretKey;

        // Log the payment intent creation attempt
        PaymentLog paymentLog = PaymentLog.builder()
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency("usd")
                .gateway("STRIPE")
                .event(PaymentEvent.PAYMENT_INTENT)
                .status(PaymentStatus.INITIATED)
                .build();
        paymentLogRepository.save(paymentLog);

        try {
            // Create PaymentIntent on Stripe with amount and currency
            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(request.getAmount().longValue())
                    .setCurrency("usd");

            PaymentIntent paymentIntent = PaymentIntent.create(builder.build());

            // Update log with successful creation
            paymentLog.setTransactionId(paymentIntent.getId());
            paymentLog.setMessage("PaymentIntent created - status: " + paymentIntent.getStatus());
            paymentLogRepository.save(paymentLog);

            // Return clientSecret for frontend to confirm payment
            Map<String, String> response = new HashMap<>();
            response.put("paymentIntentId", paymentIntent.getId());
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("status", paymentIntent.getStatus());
            response.put("message", "PaymentIntent created - status: " + paymentIntent.getStatus());
            return response;

        } catch (Exception e) {
            // Log failure and rethrow
            paymentLog.setStatus(PaymentStatus.FAILED);
            paymentLog.setMessage(e.getMessage());
            paymentLogRepository.save(paymentLog);
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    /**
     * Checks PaymentIntent status after frontend confirmation and logs it.
     *
     * Flow:
     * 1. Logs CONFIRM event with status PROCESSING
     * 2. Retrieves PaymentIntent from Stripe
     * 3. If succeeded: fetches charge ID, card details (last4, brand)
     * 4. Updates log with final status and details
     *
     * @param paymentIntentId Stripe PaymentIntent ID (e.g., "pi_xxx")
     * @param secretKey       Stripe secret key for this merchant
     * @return Map with status, chargeId, cardLast4, cardBrand, amount, message
     */
    public Map<String, String> getPaymentIntentStatus(String paymentIntentId, String secretKey, Long customerId, String paymentMethod) {
        Stripe.apiKey = secretKey;

        // Log the confirm attempt
        PaymentLog paymentLog = PaymentLog.builder()
                .customerId(customerId)
                .currency("usd")
                .gateway("STRIPE")
                .transactionId(paymentIntentId)
                .event(PaymentEvent.CONFIRM)
                .status(PaymentStatus.PROCESSING)
                .paymentMethod(paymentMethod)
                .build();
        paymentLogRepository.save(paymentLog);

        try {
            // Retrieve PaymentIntent from Stripe to get current status
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            Map<String, String> response = new HashMap<>();
            response.put("paymentIntentId", paymentIntent.getId());
            response.put("status", paymentIntent.getStatus());
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("amount", String.valueOf(paymentIntent.getAmount()));
            response.put("message", "PaymentIntent status: " + paymentIntent.getStatus());

            // If payment succeeded, fetch charge and card details
            if ("succeeded".equals(paymentIntent.getStatus())) {
                response.put("transactionId", paymentIntent.getId());
                response.put("chargeId", paymentIntent.getLatestCharge() != null ? paymentIntent.getLatestCharge() : "");
                response.put("message", "Payment successful - charge ID: " + response.get("chargeId"));

                // Fetch card details from PaymentMethod
                if (paymentIntent.getPaymentMethod() != null) {
                    com.stripe.model.PaymentMethod pm = com.stripe.model.PaymentMethod.retrieve(paymentIntent.getPaymentMethod());
                    if (pm.getCard() != null) {
                        response.put("cardLast4", pm.getCard().getLast4() != null ? pm.getCard().getLast4() : "");
                        response.put("cardBrand", pm.getCard().getBrand() != null ? pm.getCard().getBrand() : "");
                    }
                }

                // Update log with charge and card details
                paymentLog.setChargeId(response.get("chargeId"));
                paymentLog.setCardLast4(response.get("cardLast4"));
                paymentLog.setCardBrand(response.get("cardBrand"));
            }

            // Update log with amount and message
            if (response.get("amount") != null) {
                paymentLog.setAmount(new java.math.BigDecimal(response.get("amount")));
            }
            paymentLog.setMessage(response.get("message"));
            paymentLogRepository.save(paymentLog);

            return response;

        } catch (Exception e) {
            // Log failure and rethrow
            paymentLog.setStatus(PaymentStatus.FAILED);
            paymentLog.setMessage(e.getMessage());
            paymentLogRepository.save(paymentLog);
            throw new RuntimeException("Failed to retrieve payment: " + e.getMessage());
        }
    }
}
