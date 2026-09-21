package com.payment.microservice.repository;

import com.payment.microservice.model.PaymentGateway;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentGatewayRepository extends JpaRepository<PaymentGateway, Long> {
  List<PaymentGateway> findAllByOrderByCreatedAtDesc();
}
