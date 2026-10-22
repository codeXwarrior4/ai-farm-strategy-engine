package com.farmengine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health endpoint — used to verify the backend is running.
 * This endpoint is publicly accessible (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    /**
     * GET /api/v1/health
     * Returns a simple status object indicating the service is up.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ai-farm-strategy-backend",
                "version", "0.1.0",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
