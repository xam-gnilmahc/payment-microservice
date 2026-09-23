package com.payment.microservice.repository;

import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

  List<PaymentLog> findByCustomerId(Long customerId);

  List<PaymentLog> findByEmail(String email);

  List<PaymentLog> findByStatus(PaymentStatus status);

  List<PaymentLog> findAllByOrderByCreatedAtDesc();

  Page<PaymentLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Optional<PaymentLog> findByChargeId(String chargeId);

  Optional<PaymentLog> findByTransactionId(String transactionId);
}
