package com.payment.microservice.repository;

import com.payment.microservice.model.BillingAddress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingAddressRepository extends JpaRepository<BillingAddress, Long> {
  List<BillingAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);
}
