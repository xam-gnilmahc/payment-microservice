package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.model.*;
import com.payment.microservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserRepository userRepository;
    private final PaymentGatewayRepository paymentGatewayRepository;
    private final UserPaymentGatewayRepository userPaymentGatewayRepository;
    private final UserPaymentCredentialsRepository userPaymentCredentialsRepository;
    private final StripeService stripeService;
    
    /**
     * Fetches the title of the payment gateway based on its ID.
     * If the gateway is not found, it returns "UNKNOWN".
     */
    private String getGatewayTitle(Long gatewayId) {
        return paymentGatewayRepository.findById(gatewayId)
                .map(PaymentGateway::getTitle)
                .orElse("UNKNOWN");
    }
    
    /**
     * Fetches the secret key for a given customer and gateway.
     * It first retrieves the user, then checks if the gateway is assigned to the user,
     * and finally fetches the secret key from the user's payment credentials.
     * If any of these steps fail, it throws a RuntimeException with an appropriate message.
     */
    private String getSecretKey(Long customerId, Long gatewayId) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        UserPaymentGateway upg = userPaymentGatewayRepository.findByUserIdAndEnabled(user.getId(), true)
                .stream()
                .filter(u -> u.getPaymentGatewayId().equals(gatewayId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Gateway not assigned to customer"));

        return userPaymentCredentialsRepository.findByUserPaymentGatewaysId(upg.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No credentials found"))
                .getSecretKey();
    }
    
    /**
     * Initiates a payment by creating a payment intent using the Stripe service.
     * It first checks if the specified gateway is "stripe". If not, it throws a
     *  RuntimeException indicating that the gateway is not configured. If the gateway is valid,
     *  it retrieves the secret key for the customer and gateway, and then calls the Stripe service to create the payment intent.
     *  The method returns a map containing the payment intent ID, client secret, status, and a message.
     */
    public Map<String, String> initiatePayment(PaymentRequest request) {
        String gatewayTitle = getGatewayTitle(request.getGatewayId());

        if (!gatewayTitle.equalsIgnoreCase("stripe")) {
            throw new RuntimeException("This gateway is not configured yet.");
        }

        String secretKey = getSecretKey(request.getCustomerId(), request.getGatewayId());
        return stripeService.createPaymentIntent(request, secretKey);
    }
    
    /**
     * Confirms a payment by checking the status of a payment intent using the Stripe service.
     * It first checks if the specified gateway is "stripe". If not, it throws a
     *  RuntimeException indicating that the gateway is not configured. If the gateway is valid,
     *  it retrieves the secret key for the customer and gateway, and then calls the Stripe service to get the payment intent status.
     *  The method returns a map containing the payment intent ID, client secret, status, and a message.
     */
    public Map<String, String> confirmPayment(String paymentIntentId, Long customerId, Long gatewayId, String paymentMethod) {
        String gatewayTitle = gatewayId != null ? getGatewayTitle(gatewayId) : "UNKNOWN";

        if (!gatewayTitle.equalsIgnoreCase("stripe")) {
            throw new RuntimeException("This gateway is not configured yet.");
        }

        String secretKey = getSecretKey(customerId, gatewayId);
        return stripeService.getPaymentIntentStatus(paymentIntentId, secretKey, customerId, paymentMethod);
    }
}
