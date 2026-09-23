package com.payment.microservice.repository;

import com.payment.microservice.model.UserPaymentGateway;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPaymentGatewayRepository extends JpaRepository<UserPaymentGateway, Long> {
  List<UserPaymentGateway> findByUserIdAndEnabled(Long userId, Boolean enabled);

  List<UserPaymentGateway> findByPaymentGatewayId(Long paymentGatewayId);

  Optional<UserPaymentGateway> findByUserIdAndPaymentGatewayId(Long userId, Long paymentGatewayId);
}
