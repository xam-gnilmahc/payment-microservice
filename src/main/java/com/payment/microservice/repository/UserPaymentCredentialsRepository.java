package com.payment.microservice.repository;

import com.payment.microservice.model.UserPaymentCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPaymentCredentialsRepository extends JpaRepository<UserPaymentCredentials, Long> {
    List<UserPaymentCredentials> findByUserPaymentGatewaysId(Long userPaymentGatewaysId);
}
