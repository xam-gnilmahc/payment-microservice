package com.payment.microservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
  
  /**
   * Serve the payment page. This endpoint forwards the request to payment.html, which is expected to be in the static resources folder. The frontend will handle the rendering of the payment page.
   * @return A string indicating the path to forward to the payment page.
   */
  @GetMapping("/pay")
  public String paymentPage() {
    return "forward:/payment.html";
  }
}
