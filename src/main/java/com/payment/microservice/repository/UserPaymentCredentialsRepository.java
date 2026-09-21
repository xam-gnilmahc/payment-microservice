package com.payment.microservice.repository;

import com.payment.microservice.model.UserPaymentCredentials;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPaymentCredentialsRepository
    extends JpaRepository<UserPaymentCredentials, Long> {
  List<UserPaymentCredentials> findByUserPaymentGatewaysId(Long userPaymentGatewaysId);
}
