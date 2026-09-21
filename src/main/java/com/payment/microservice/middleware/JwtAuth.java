package com.payment.microservice.middleware;

import com.payment.microservice.model.User;
import com.payment.microservice.repository.UserRepository;
import com.payment.microservice.token.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuth extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final UserRepository userRepository;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/api/v1/webhooks")
        || path.startsWith("/api/v1/auth")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.equals("/payment.html")
        || path.equals("/index.html")
        || path.startsWith("/stripe/")
        || path.startsWith("/authorize/")
        || path.startsWith("/css/")
        || path.startsWith("/common/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);

      if (jwtUtil.validateToken(token)) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
          UsernamePasswordAuthenticationToken auth =
              new UsernamePasswordAuthenticationToken(user, null, new ArrayList<>());
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}
