package com.payment.microservice.controller;

import com.payment.microservice.dto.ChangePasswordRequest;
import com.payment.microservice.dto.LoginRequest;
import com.payment.microservice.dto.RegisterRequest;
import com.payment.microservice.model.User;
import com.payment.microservice.repository.UserRepository;
import com.payment.microservice.service.LoginRateLimiter;
import com.payment.microservice.traits.ApiResponse;
import com.payment.microservice.traits.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

//@RestController = this class handles HTTP requests and returns JSON responses
// @RequestMapping("/api/v1/auth") = all endpoints in this controller start with /api/v1/auth
// @RequiredArgsConstructor = Spring injects the final fields below (dependency injection)
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  // These are injected automatically by Spring (dependency injection)
  private final UserRepository userRepository; // database operations for User table
  private final PasswordEncoder passwordEncoder; // encrypt/decrypt passwords
  private final AuthenticationManager authenticationManager; // checks email + password
  private final SecurityContextRepository securityContextRepository;
  private final LoginRateLimiter loginRateLimiter;


  /**
   * register endpoint: creates a new user, hashes the password, saves to database, and logs them in. Returns 201 Created with user info, or 400 Bad Request if the email already exists.
   */
  @PostMapping("/register")
  public ResponseEntity<ApiResponse<Object>> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    // Check if email already exists in database
    if (userRepository.existsByEmail(request.getEmail())) {
      return ResponseEntity.badRequest().body(ApiResponse.error("Email already exists", 400));
    }

    // Create new user with hashed password
    User user =
        User.builder()
            .name(request.getName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword())) // BCrypt encryption
            .isActive(true)
            .build();

    // Save user to database
    userRepository.save(user);
    
    // Log the user in by creating an Authentication and saving it to the session
    User signedIn = signIn(user.getEmail(), request.getPassword(), httpRequest, httpResponse);

    // Return 201 Created with user info
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                "User registered successfully", 201, new UserResponse(signedIn)));
  }

  /**
   * login endpoint: checks email/password, handles failed attempts, and logs the user in. 
   * Returns 200 OK with user info, 400 Bad Request for wrong credentials, 403 Forbidden for blocked accounts, or 429 Too Many Requests 
   * if the user is temporarily blocked due to too many failed attempts.
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<Object>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
      
    // Check if the user is blocked due to too many failed login attempts
    if (loginRateLimiter.isBlocked(request.getEmail())) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(ApiResponse.error(LoginRateLimiter.LOCKED_MESSAGE, 429));
    }

    User user;

    try {

      user = signIn(request.getEmail(), request.getPassword(), httpRequest, httpResponse);

    } catch (DisabledException e) {

      // The account is blocked. Return 403 Forbidden with a message.
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(
              ApiResponse.error(
                  "Your account has been blocked. Please contact the administrator.", 403));

    } catch (AuthenticationException e) {

      // The email/password is wrong. Record the failed attempt and check if the user should be blocked.
      if (loginRateLimiter.recordFailure(request.getEmail())) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ApiResponse.error(LoginRateLimiter.LOCKED_MESSAGE, 429));
      }
      
      return ResponseEntity.badRequest().body(ApiResponse.error("Invalid email or password", 400));
    }

    loginRateLimiter.reset(request.getEmail());
    // Return 200 OK
    return ResponseEntity.ok(ApiResponse.success("Login successful", 200, new UserResponse(user)));
  }

  // Who is this browser session? The frontend calls this on every page load to find out.
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Object>> me(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(
              ApiResponse.error(
                  "Your session has ended, or your account has been blocked. Please sign in again to continue.",
                  401));
    }
    return ResponseEntity.ok(ApiResponse.success("Current session fetched", 200, new UserResponse(user)));
  }

  /**
   * change-password endpoint: replaces the signed-in user's password with the new one. No
   * current password is checked, so the form only asks for the new password twice. Returns 200 OK
   * on success, 400 if the new password fails validation, 401 if nobody is signed in.
   */
  @PostMapping("/change-password")
  public ResponseEntity<ApiResponse<Object>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    User signedIn = CurrentUser.get();
    if (signedIn == null) {
      return sessionEnded();
    }
    User user = userRepository.findById(signedIn.getId()).orElse(null);
    if (user == null) {
      return sessionEnded();
    }
    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
    return ResponseEntity.ok(ApiResponse.success("Password changed successfully", 200, null));
  }

  private ResponseEntity<ApiResponse<Object>> sessionEnded() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiResponse.error(
                "Your session has ended, or your account has been blocked. Please sign in again to continue.",
                401));
  }

  /**
   * logout endpoint: invalidates the session and clears the SecurityContext, effectively signing the user out. Returns 200 OK with a message.
   */
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest httpRequest) {
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok(ApiResponse.success("Logged out", 200, null));
  }

  /**
   * Helper method to authenticate a user and store their authentication in the session. Returns the authenticated User object. Throws AuthenticationException if authentication fails.
   */
  private User signIn(
      String email, String password, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    Authentication authentication;
    try {
      // Spring Security checks the email and password against the database. If the account is disabled (blocked), it throws DisabledException. If the email/password is wrong, it throws BadCredentialsException.
      authentication =
          authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    } catch (DisabledException e) {
      // Spring checks isEnabled() before it compares passwords, so a blocked account throws here
      // whatever password was sent. Reporting "blocked" straight away would let anyone discover
      // which addresses are registered, so only say it once the password is actually correct.
      User blocked = userRepository.findByEmail(email).orElse(null);
      boolean passwordIsRight =
          blocked != null && passwordEncoder.matches(password, blocked.getPassword());
      if (!passwordIsRight) {
        throw new BadCredentialsException("Invalid email or password");
      }
      throw e;
    }

    // Store the Authentication in the SecurityContext and save it to the session
    SecurityContextHolder.getContext().setAuthentication(authentication);
    securityContextRepository.saveContext(
        SecurityContextHolder.getContext(), httpRequest, httpResponse);

    return (User) authentication.getPrincipal();
  }

  /**
   * UserResponse record: a simple DTO to return user info in API responses. It includes userId, email, name, and isSuperAdmin flag. The constructor takes a User entity and extracts the relevant fields.
   */
  public record UserResponse(Long userId, String email, String name, String isSuperAdmin) {
    public UserResponse(User user) {
      this(
          user.getId(),
          user.getEmail(),
          user.getName(),
          user.getIsSuperAdmin() != null ? user.getIsSuperAdmin() : "0");
    }
  }
}
