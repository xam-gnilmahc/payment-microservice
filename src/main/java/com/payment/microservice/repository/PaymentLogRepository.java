package com.payment.microservice.repository;

import com.payment.microservice.model.PaymentLog;
import com.payment.microservice.model.PaymentStatus;
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
public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

  List<PaymentLog> findByCustomerId(Long customerId);

  Page<PaymentLog> findByCustomerId(Long customerId, Pageable pageable);

  List<PaymentLog> findByEmail(String email);

  List<PaymentLog> findByStatus(PaymentStatus status);

  @Query("SELECT p FROM PaymentLog p ORDER BY p.id DESC")
  Page<PaymentLog> findAllByIdDesc(Pageable pageable);

  Page<PaymentLog> findByStatusOrderByIdDesc(PaymentStatus status, Pageable pageable);

  Page<PaymentLog> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);

  Page<PaymentLog> findByCustomerIdAndStatusOrderByIdDesc(
      Long customerId, PaymentStatus status, Pageable pageable);

  Optional<PaymentLog> findByChargeId(String chargeId);

  Optional<PaymentLog> findByTransactionId(String transactionId);

  @Query("SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.status = :status")
  long countByCreatedAtAfterAndStatus(
      @Param("startDate") LocalDateTime startDate, @Param("status") PaymentStatus status);

  @Query("SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate")
  long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT COALESCE(p.paymentMethod, 'Unknown'), COUNT(p) FROM PaymentLog p "
          + "WHERE p.createdAt >= :startDate GROUP BY p.paymentMethod")
  List<Object[]> countGroupByPaymentMethod(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT p.status, COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate GROUP BY p.status")
  List<Object[]> countGroupByStatus(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT FUNCTION('DATE', p.createdAt), COUNT(p), COALESCE(SUM(p.amount), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate GROUP BY FUNCTION('DATE', p.createdAt)")
  List<Object[]> dailyCountsAndRevenue(@Param("startDate") LocalDateTime startDate);
}
