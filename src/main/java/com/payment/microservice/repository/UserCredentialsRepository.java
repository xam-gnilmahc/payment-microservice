package com.payment.microservice.repository;

import com.payment.microservice.model.UserCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, Long> {

    List<UserCredentials> findByUserId(Integer userId);
}
