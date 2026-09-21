package com.payment.microservice.traits;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private String message;
  private int statusCode;
  private T data;

  public static <T> ApiResponse<T> success(String message, int statusCode, T data) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .statusCode(statusCode)
        .data(data)
        .build();
  }

  public static <T> ApiResponse<T> error(String message, int statusCode) {
    return ApiResponse.<T>builder().success(false).message(message).statusCode(statusCode).build();
  }
}
