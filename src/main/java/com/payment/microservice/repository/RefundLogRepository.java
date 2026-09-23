package com.payment.microservice.repository;

import com.payment.microservice.model.RefundLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundLogRepository extends JpaRepository<RefundLog, Long> {

  Optional<RefundLog> findByRefundId(String refundId);

  List<RefundLog> findByChargeId(String chargeId);

  List<RefundLog> findByCustomerId(Long customerId);
}
