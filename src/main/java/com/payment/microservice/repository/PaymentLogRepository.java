package com.payment.microservice.repository;

import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

    List<PaymentLog> findByCustomerId(Long customerId);

    List<PaymentLog> findByStatus(PaymentStatus status);

    List<PaymentLog> findAllByOrderByCreatedAtDesc();
}
