package com.payment.microservice.repository;

import com.payment.microservice.model.PaymentGateway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGatewayRepository extends JpaRepository<PaymentGateway, Long> {
    List<PaymentGateway> findAllByOrderByCreatedAtDesc();
}
