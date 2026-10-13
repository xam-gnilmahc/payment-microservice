package com.payment.microservice.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LoginRateLimiter is a Spring component that tracks failed login attempts for users and blocks further attempts after a certain threshold. 
 * It uses a ConcurrentHashMap to store the number of failures and lockout time for each user, identified by their email address. 
 * The class provides methods to check if a user is blocked, record a failed login attempt, reset the attempt count, and purge expired attempts periodically.
 */
@Component
public class LoginRateLimiter {

  public static final int MAX_ATTEMPTS = 5;
  public static final long LOCKOUT_MILLIS = 60_000;
  public static final String LOCKED_MESSAGE = "Too many failed login attempts. Please wait 1 minute and try again.";

  private static final class Attempt {
    private int failures;
    private long lockedUntil;
    private long lastFailureAt;
  }

  private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
  
  // Check if the user is currently blocked from logging in based on their email address.
  public boolean isBlocked(String email) {
    String key = key(email);
    Attempt attempt = attempts.get(key);
    if (attempt == null) {
      return false;
    }
    long now = System.currentTimeMillis();
    synchronized (attempt) {
      if (attempt.lockedUntil > now) {
        return true;
      }
      if (attempt.lockedUntil > 0) {
        attempts.remove(key, attempt);
      }
      return false;
    }
  }
  
  // Record a failed login attempt for the user identified by their email address. 
  // If the number of failed attempts exceeds the maximum allowed, the user is locked out for a specified duration.
  public boolean recordFailure(String email) {
    String key = key(email);
    Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
    synchronized (attempt) {
      long now = System.currentTimeMillis();
      if (attempt.lockedUntil > 0 && attempt.lockedUntil <= now) {
        attempt.failures = 0;
        attempt.lockedUntil = 0;
      }
      attempt.failures++;
      attempt.lastFailureAt = now;
      if (attempt.failures >= MAX_ATTEMPTS) {
        attempt.lockedUntil = now + LOCKOUT_MILLIS;
        return true;
      }
      return false;
    }
  }
  
  // Reset the failed login attempt count for the user identified by their email address, effectively allowing them to attempt to log in again without being blocked.
  public void reset(String email) {
    attempts.remove(key(email));
  }
  
  // Periodically purge expired attempts from the attempts map. 
  // This method is scheduled to run every 60 seconds and removes entries where the lockout period has expired and the last failure was long enough ago.
  @Scheduled(fixedRate = 60_000)
  public void purgeExpired() {
    long now = System.currentTimeMillis();
    attempts
        .entrySet()
        .removeIf(
            entry -> {
              Attempt attempt = entry.getValue();
              synchronized (attempt) {
                return attempt.lockedUntil <= now
                    && now - attempt.lastFailureAt >= LOCKOUT_MILLIS;
              }
            });
  }
  
  // Generate a normalized key for the user's email address by trimming whitespace and converting it to lowercase. 
  // This ensures consistent handling of email addresses in the attempts map.
  private String key(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
