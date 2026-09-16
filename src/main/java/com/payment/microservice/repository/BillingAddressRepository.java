package com.payment.microservice.repository;

import com.payment.microservice.model.BillingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillingAddressRepository extends JpaRepository<BillingAddress, Long> {
    List<BillingAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);
}
