package com.orderplatform.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "auth-service");
        return status;
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("name", "Auth Service");
        info.put("version", "1.0.0");
        info.put("description", "Authentication and Authorization Service");
        return info;
    }
}