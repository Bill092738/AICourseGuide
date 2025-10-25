package com.courseguide;

import com.courseguide.dto.StudentProfile;
import com.courseguide.dto.UserBasicInfo;
import com.courseguide.processors.RecommendationEngine;
import com.courseguide.services.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RecommendationEngine engine = new RecommendationEngine();

    @Autowired
    private FileStorageService storage;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // Legacy/simple JSON endpoint preserved
    @PostMapping("/recommendations")
    public Map<String, List<String>> recommendations(@RequestBody Map<String, Object> body) {
        String major = (String) body.getOrDefault("major", "");
        double gpa = 0.0;
        try { gpa = Double.parseDouble(body.getOrDefault("gpa", "0").toString()); } catch (Exception ignore) {}

        List<String> recs = engine.generateRecommendations(major, gpa);
        return Map.of("recommendations", recs);
    }

    // New: upload a single PDF and temp-store it; returns an ID to reference later
    @PostMapping(value = "/upload-progress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadProgress(@RequestPart("progressPdf") MultipartFile progressPdf) {
        if (progressPdf == null || progressPdf.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing progressPdf");
        }
        String ctype = progressPdf.getContentType() == null ? "" : progressPdf.getContentType();
        String filename = progressPdf.getOriginalFilename() == null ? "" : progressPdf.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!ctype.contains("pdf") && !filename.endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF is allowed");
        }
        try {
            String id = storage.store(progressPdf);
            return Map.of("status", "stored", "progressFileId", id);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    // New: richer profile-based recommendations (JSON), referencing the uploaded PDF by ID
    @PostMapping(value = "/recommendations/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, List<String>> recommendationsForProfile(@RequestBody StudentProfile profile) {
        // Prefer target goal major; fallback to user's current major
        String major =
            profile.target() != null && profile.target().major() != null && !profile.target().major().isBlank()
                ? profile.target().major()
                : Optional.ofNullable(profile.user()).map(UserBasicInfo::major).orElse("");

        // Placeholder GPA until PDF parsing is implemented
        double gpa = 0.0;

        // You can access the uploaded PDF path if needed:
        if (profile.progressFileId() != null && !profile.progressFileId().isBlank()) {
            // Path filePath = storage.resolve(profile.progressFileId());
            // TODO: parse PDF for current/completed courses and transfer credits
        }

        List<String> recs = engine.generateRecommendations(major, gpa);
        return Map.of("recommendations", recs);
    }
}