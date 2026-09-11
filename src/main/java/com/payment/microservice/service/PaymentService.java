package com.payment.microservice.service;

import com.payment.microservice.dto.PaymentRequest;
import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.User;
import com.payment.microservice.model.UserCredentials;
import com.payment.microservice.repository.PaymentLogRepository;
import com.payment.microservice.repository.UserCredentialsRepository;
import com.payment.microservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final UserRepository userRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final StripeService stripeService;
    private final PaymentLogRepository paymentLogRepository;

    // This method handles the complete payment flow.
    // When a customer makes a payment, this method finds the merchant's payment credentials
    // from the database based on the gateway (currently supports Stripe, more coming soon).
    // It then calls the appropriate payment gateway API to process the payment.
    // Before calling the gateway, it creates a log entry with INITIATED status.
    // After the gateway responds, it updates the log with SUCCESS or FAILED status
    // along with the transaction details. The log also tracks how long the payment took.
    public Map<String, String> processPayment(PaymentRequest request) {
        LocalDateTime startTime = LocalDateTime.now();

        User user = userRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + request.getCustomerId()));

        List<UserCredentials> allCredentials = userCredentialsRepository.findByUserId(user.getId().intValue());

        UserCredentials credentials = null;
        String gatewayName = null;

        for (UserCredentials cred : allCredentials) {
            if (cred.getGateway() == 0) {
                credentials = cred;
                gatewayName = "STRIPE";
                break;
            }
        }

        if (credentials == null) {
            throw new RuntimeException("No payment credentials found for customer: " + request.getCustomerId());
        }

        PaymentLog paymentLog = PaymentLog.builder()
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency("usd")
                .gateway(gatewayName)
                .status("INITIATED")
                .startTime(startTime)
                .build();

        paymentLogRepository.save(paymentLog);

        try {
            Map<String, String> result = stripeService.processPayment(request, credentials.getSecretKey());

            LocalDateTime endTime = LocalDateTime.now();
            paymentLog.setEndTime(endTime);
            paymentLog.setDurationMs(java.time.Duration.between(startTime, endTime).toMillis());
            paymentLog.setStatus("SUCCESS");
            paymentLog.setTransactionId(result.get("transactionId"));
            paymentLog.setChargeId(result.get("chargeId"));
            paymentLogRepository.save(paymentLog);

            return result;

        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            paymentLog.setEndTime(endTime);
            paymentLog.setDurationMs(java.time.Duration.between(startTime, endTime).toMillis());
            paymentLog.setStatus("FAILED");
            paymentLog.setErrorMessage(e.getMessage());
            paymentLogRepository.save(paymentLog);

            throw e;
        }
    }
}
