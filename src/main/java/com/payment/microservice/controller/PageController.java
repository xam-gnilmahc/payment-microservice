package com.payment.microservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/pay")
    public String paymentPage() {
        return "forward:/payment.html";
    }
}
