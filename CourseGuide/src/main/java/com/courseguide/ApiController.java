package com.courseguide;

import org.springframework.web.bind.annotation.*;
import java.util.*;

import com.courseguide.processors.RecommendationEngine;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RecommendationEngine engine = new RecommendationEngine();

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/recommendations")
    public Map<String, List<String>> recommendations(@RequestBody Map<String, Object> body) {
        String major = (String) body.getOrDefault("major", "");
        double gpa = 0.0;
        try { gpa = Double.parseDouble(body.getOrDefault("gpa", "0").toString()); } catch (Exception ignore) {}

        List<String> recs = engine.generateRecommendations(major, gpa);

        return Map.of("recommendations", recs);
    }
}