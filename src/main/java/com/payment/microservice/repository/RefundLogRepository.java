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

  List<RefundLog> findByChargeId(String chargeId);

  Page<RefundLog> findByCustomerId(Long customerId, Pageable pageable);

  @Query("SELECT r FROM RefundLog r ORDER BY r.id DESC")
  Page<RefundLog> findAllByIdDesc(Pageable pageable);

  Page<RefundLog> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);


  List<RefundLog> findByEmail(String email);

  @Query(
      "SELECT COUNT(DISTINCT r.refundId) FROM RefundLog r WHERE r.createdAt >= :startDate "
          + "AND r.createdAt < :endDate AND r.status = '1'")
  long countSucceededBetween(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT COUNT(DISTINCT r.refundId) FROM RefundLog r WHERE r.createdAt >= :startDate "
          + "AND r.createdAt < :endDate AND r.status = '1' AND r.customerId = :customerId")
  long countSucceededBetweenAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT COALESCE(SUM(r.amount), 0) FROM RefundLog r WHERE r.id IN ("
          + "SELECT MIN(r2.id) FROM RefundLog r2 WHERE r2.createdAt >= :startDate "
          + "AND r2.createdAt < :endDate AND r2.status = '1' GROUP BY r2.refundId)")
  double sumSucceededAmountBetween(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT COALESCE(SUM(r.amount), 0) FROM RefundLog r WHERE r.id IN ("
          + "SELECT MIN(r2.id) FROM RefundLog r2 WHERE r2.createdAt >= :startDate "
          + "AND r2.createdAt < :endDate AND r2.status = '1' AND r2.customerId = :customerId "
          + "GROUP BY r2.refundId)")
  double sumSucceededAmountBetweenAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);
}
