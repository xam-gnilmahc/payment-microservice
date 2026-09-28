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

  @Query(
      "SELECT p FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "ORDER BY p.id DESC")
  List<PaymentLog> findBetween(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT p FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId ORDER BY p.id DESC")
  List<PaymentLog> findBetweenAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query("SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.status = :status")
  long countByCreatedAtAfterAndStatus(
      @Param("startDate") LocalDateTime startDate, @Param("status") PaymentStatus status);

  @Query("SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate")
  long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate "
          + "AND p.status = :status AND p.customerId = :customerId")
  long countByCreatedAtAfterAndStatusAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("status") PaymentStatus status,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.customerId = :customerId")
  long countByCreatedAtAfterAndCustomer(
      @Param("startDate") LocalDateTime startDate, @Param("customerId") Long customerId);

  @Query(
      "SELECT COALESCE(p.paymentMethod, 'Unknown'), COUNT(p) FROM PaymentLog p "
          + "WHERE p.createdAt >= :startDate GROUP BY p.paymentMethod")
  List<Object[]> countGroupByPaymentMethod(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT COALESCE(p.paymentMethod, 'Unknown'), COUNT(p) FROM PaymentLog p "
          + "WHERE p.createdAt >= :startDate AND p.customerId = :customerId GROUP BY p.paymentMethod")
  List<Object[]> countGroupByPaymentMethodAndCustomer(
      @Param("startDate") LocalDateTime startDate, @Param("customerId") Long customerId);

  @Query(
      "SELECT p.status, COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate GROUP BY p.status")
  List<Object[]> countGroupByStatus(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT p.status, COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate "
          + "AND p.customerId = :customerId GROUP BY p.status")
  List<Object[]> countGroupByStatusAndCustomer(
      @Param("startDate") LocalDateTime startDate, @Param("customerId") Long customerId);

  @Query(
      "SELECT FUNCTION('DATE', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
  List<Object[]> dailyBreakdown(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT FUNCTION('DATE', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId "
          + "GROUP BY FUNCTION('DATE', p.createdAt) ORDER BY FUNCTION('DATE', p.createdAt)")
  List<Object[]> dailyBreakdownAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m'), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "GROUP BY FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m') "
          + "ORDER BY FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m')")
  List<Object[]> monthlyBreakdown(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m'), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId "
          + "GROUP BY FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m') "
          + "ORDER BY FUNCTION('DATE_FORMAT', p.createdAt, '%Y-%m')")
  List<Object[]> monthlyBreakdownAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT FUNCTION('YEAR', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "GROUP BY FUNCTION('YEAR', p.createdAt) ORDER BY FUNCTION('YEAR', p.createdAt)")
  List<Object[]> yearlyBreakdown(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT FUNCTION('YEAR', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId "
          + "GROUP BY FUNCTION('YEAR', p.createdAt) ORDER BY FUNCTION('YEAR', p.createdAt)")
  List<Object[]> yearlyBreakdownAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v'), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "GROUP BY FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v') "
          + "ORDER BY FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v')")
  List<Object[]> weeklyBreakdown(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v'), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED THEN 1 ELSE 0 END), 0), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.FAILED THEN 1 ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId "
          + "GROUP BY FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v') "
          + "ORDER BY FUNCTION('DATE_FORMAT', p.createdAt, '%x-W%v')")
  List<Object[]> weeklyBreakdownAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT FUNCTION('DATE', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate GROUP BY FUNCTION('DATE', p.createdAt)")
  List<Object[]> dailyCountsAndRevenue(@Param("startDate") LocalDateTime startDate);

  @Query(
      "SELECT FUNCTION('DATE', p.createdAt), COUNT(p), "
          + "COALESCE(SUM(CASE WHEN p.status = com.payment.microservice.model.PaymentStatus.SUCCEEDED "
          + "THEN p.amount ELSE 0 END), 0) "
          + "FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.customerId = :customerId "
          + "GROUP BY FUNCTION('DATE', p.createdAt)")
  List<Object[]> dailyCountsAndRevenueAndCustomer(
      @Param("startDate") LocalDateTime startDate, @Param("customerId") Long customerId);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate")
  long countBetween(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.customerId = :customerId")
  long countBetweenAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("customerId") Long customerId);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.status = :status")
  long countBetweenAndStatus(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("status") PaymentStatus status);

  @Query(
      "SELECT COUNT(p) FROM PaymentLog p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate "
          + "AND p.status = :status AND p.customerId = :customerId")
  long countBetweenAndStatusAndCustomer(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("status") PaymentStatus status,
      @Param("customerId") Long customerId);
}
