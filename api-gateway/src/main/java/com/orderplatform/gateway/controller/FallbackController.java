package com.orderplatform.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public Mono<Map<String, String>> authFallback() {
        final Map<String, String> response = new HashMap<>();
        response.put("error", "Auth service is unavailable. Please try again later.");
        response.put("status", "503");
        return Mono.just(response);
    }

    @GetMapping("/user")
    public Mono<Map<String, String>> userFallback() {
        final Map<String, String> response = new HashMap<>();
        response.put("error", "User service is unavailable. Please try again later.");
        response.put("status", "503");
        return Mono.just(response);
    }

    @GetMapping("/product")
    public Mono<Map<String, String>> productFallback() {
        final Map<String, String> response = new HashMap<>();
        response.put("error", "Product service is unavailable. Please try again later.");
        response.put("status", "503");
        return Mono.just(response);
    }
}
