package com.payment.microservice.repository;

import com.payment.microservice.model.RefundLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundLogRepository extends JpaRepository<RefundLog, Long> {

  Optional<RefundLog> findByRefundId(String refundId);

  List<RefundLog> findByChargeId(String chargeId);

  List<RefundLog> findByCustomerId(Long customerId);

  Page<RefundLog> findByCustomerId(Long customerId, Pageable pageable);

  @Query("SELECT r FROM RefundLog r ORDER BY r.id DESC")
  Page<RefundLog> findAllByIdDesc(Pageable pageable);

  Page<RefundLog> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);

  @Query("SELECT COUNT(r) FROM RefundLog r WHERE r.createdAt >= :startDate AND r.status = '1'")
  long countSucceededByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
}
