package com.payment.microservice.repository;

import com.payment.microservice.model.UserCredentials;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, Long> {

  List<UserCredentials> findByUserId(Integer userId);

  Optional<UserCredentials> findByUserIdAndGateway(Integer userId, Integer gateway);
}
