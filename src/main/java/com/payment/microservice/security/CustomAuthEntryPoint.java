package com.payment.microservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.microservice.traits.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
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

  // Called when a request needs a signed-in user and there is none -> 401
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(response, HttpStatus.UNAUTHORIZED, "Not signed in");
  }

  // Called when the request has a session but not enough permission, e.g. a normal user opening
  // /api/v1/admin -> 403
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, "Forbidden");
  }

  private void write(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message, status.value())));
  }
}
