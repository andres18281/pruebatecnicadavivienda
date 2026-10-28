package com.techdocs.document.infrastructure.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health/ready")
    public Map<String, String> ready() {
        return Map.of("status", "UP");
    }
}
