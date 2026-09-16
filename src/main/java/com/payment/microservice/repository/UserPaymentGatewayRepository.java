package com.payment.microservice.repository;

import com.payment.microservice.model.UserPaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPaymentGatewayRepository extends JpaRepository<UserPaymentGateway, Long> {
    List<UserPaymentGateway> findByUserIdAndEnabled(Long userId, Boolean enabled);
    List<UserPaymentGateway> findByPaymentGatewayId(Long paymentGatewayId);
}
