package com.payment.microservice.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Handles JWT token creation and validation
@Component
public class JwtUtil {

  @Value("${jwt.secret:myDefaultSecretKeyForTesting12345678901234567890}")
  private String secret;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
  }

  // Generate token with email and userId (no expiration - never expires)
  public String generateToken(String email, Long userId, String name) {
    return Jwts.builder()
        .subject(email)
        .claim("userId", userId)
        .claim("name", name)
        .issuedAt(new Date())
        .signWith(getSigningKey())
        .compact();
  }

  public String extractName(String token) {
    return extractClaims(token).get("name", String.class);
  }

  // Extract email from token
  public String extractEmail(String token) {
    return extractClaims(token).getSubject();
  }

  // Extract userId from token
  public Long extractUserId(String token) {
    return extractClaims(token).get("userId", Long.class);
  }

  // Validate token (always valid - no expiration)
  public boolean validateToken(String token) {
    try {
      extractClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
  }
}
