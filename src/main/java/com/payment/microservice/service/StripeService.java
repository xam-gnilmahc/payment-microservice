package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import com.stripe.Stripe;
import com.stripe.model.Charge;
import com.stripe.param.ChargeCreateParams;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

    // This method communicates with Stripe API to process the payment.
    // First, it initializes Stripe with the merchant's secret key (from database).
    // Then it builds the charge parameters: amount (converted to cents), currency,
    // the token from frontend (card reference), and optional shipping details.
    // Finally, it calls Stripe's Charge.create() API which processes the payment
    // and returns the charge details including charge ID and status.
    public Map<String, String> processPayment(PaymentRequest request, String secretKey) {
        // Initialize Stripe
        Stripe.apiKey = secretKey;

        try {
            // Create charge directly with token
            ChargeCreateParams.Builder builder = ChargeCreateParams.builder()
                    .setAmount(request.getAmount().multiply(new java.math.BigDecimal("100")).longValue())
                    .setCurrency("usd")
                    .setSource(request.getToken())
                    .setDescription("Payment for order");

            if (request.getEmail() != null) {
                builder.setReceiptEmail(request.getEmail());
            }

            if (request.getName() != null && request.getAddressLine1() != null) {
                builder.setShipping(ChargeCreateParams.Shipping.builder()
                        .setName(request.getName())
                        .setAddress(ChargeCreateParams.Shipping.Address.builder()
                                .setLine1(request.getAddressLine1())
                                .setLine2(request.getAddressLine2())
                                .setCity(request.getCity())
                                .setState(request.getState())
                                .setPostalCode(request.getZipCode())
                                .setCountry(request.getCountry())
                                .build())
                        .build());
            }

            Charge charge = Charge.create(builder.build());

            Map<String, String> response = new HashMap<>();
            response.put("transactionId", charge.getId());
            response.put("chargeId", charge.getId());
            response.put("status", charge.getStatus());

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }
}
