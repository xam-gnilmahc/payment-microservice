package com.payment.microservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.microservice.traits.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// The browser gets JSON when it is not allowed in, instead of Spring Security's default HTML
// login page. The frontend reads success/message, so it has to stay the same shape as ApiResponse.
@Component
@RequiredArgsConstructor
public class CustomAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  // Called when the request has no session, e.g. a user opening /api/v1/admin while not signed in -> 401
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(response, HttpStatus.UNAUTHORIZED, "Please sign in back in to continue");
  }

  // Called when the request has a session but the user is not allowed to do what they are trying -> 403
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
  }

  private void write(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message, status.value())));
  }
}
