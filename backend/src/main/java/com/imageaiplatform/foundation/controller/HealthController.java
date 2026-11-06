package com.imageaiplatform.foundation.controller;

import java.util.Map;
import com.imageaiplatform.auth.controller.PublicApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    @PublicApi
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}

