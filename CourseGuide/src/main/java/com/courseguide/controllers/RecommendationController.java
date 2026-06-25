package com.courseguide.controllers;

import com.courseguide.dto.StudentProfile;
import com.courseguide.dto.UserBasicInfo;
import com.courseguide.dto.LlmConfig;
import com.courseguide.processors.RecommendationEngine;
import com.courseguide.services.FileStorageService;
import com.courseguide.services.WebPagePdfService;
import com.courseguide.services.LlamaAnalysisService;
import com.courseguide.services.DuckDuckGoSearchService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Controller for recommendation endpoints.
 * Handles generating course recommendations and course plans.
 */
@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationEngine engine = new RecommendationEngine();

    @Autowired
    private FileStorageService storage;

    @Autowired
    private WebPagePdfService pdfService;

    @Autowired
    private LlamaAnalysisService llamaAnalysisService;

    @Autowired
    private DuckDuckGoSearchService searchService;

    // Public student info object for storing received data
    public static Map<String, Object> studentInfo = new HashMap<>();

    /**
     * Health check endpoint.
     *
     * @return a map containing the status
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    /**
     * Generates course recommendations based on student information.
     * Stores received data, builds search query, resolves URL, generates PDF,
     * and triggers LLM analysis for course plan generation.
     *
     * @param body the request body containing student information and optional LLM config
     * @return a map containing recommendations, summary sentence, and course plan path
     */
    @PostMapping("/recommendations")
    public Map<String, Object> recommendations(@RequestBody Map<String, Object> body) {
        // Store all received fields in the public studentInfo object
        studentInfo.clear();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            studentInfo.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
        }

        // Extract optional LLM config from request body
        LlmConfig llmConfig = null;
        Object llmConfigObj = body.get("llmConfig");
        if (llmConfigObj instanceof Map<?, ?> map) {
            llmConfig = new LlmConfig(
                (String) map.get("apiBaseUrl"),
                (String) map.get("apiKey"),
                (String) map.get("modelName")
            );
        }

        // Call debug function to print all info
        printStudentInfo(studentInfo);
        String sentence = sentenceGeneration(studentInfo);

        // Build a search-friendly query instead of using the human sentence
        String searchQuery = buildSearchQuery(studentInfo);

        // Call DuckDuckGo to resolve a URL for the built query (with HTML fallback)
        Optional<String> maybeUrl = searchService.resolveSearchUrl(searchQuery);
        String duckUrl = maybeUrl.orElse("");
        debugPrintSnapshotUrl("recommendations", duckUrl);

        String coursePlanCsvPath = "";
        boolean pdfSuccess = false;
        byte[] pdf = null;

        if (isValidHttpUrl(duckUrl)) {
            try {
                System.out.println("Attempting to generate PDF for: " + duckUrl);
                pdf = pdfService.renderExpandedPageToPdf(duckUrl);
                pdfSuccess = pdf != null && pdf.length > 0;
                debugPdfGeneration(pdfSuccess, pdf != null ? pdf.length : 0);
            } catch (Exception ex) {
                System.err.println("---- Snapshot PDF render failed ----");
                System.err.println("Message: " + ex.getMessage());
                debugPdfGeneration(false, 0);
            }

            if (pdfSuccess) {
                try {
                    Path snapshotDir = Paths.get(System.getProperty("user.dir")).resolve("snapshots");
                    Path snapshotPdf = findMostRecentPdf(snapshotDir);

                    Path progressPdf = null;
                    String progressFileId = Objects.toString(studentInfo.get("progressFileId"), "");
                    if (!progressFileId.isEmpty()) {
                        progressPdf = storage.resolve(progressFileId);
                        System.out.println("Progress PDF found at: " + progressPdf.toAbsolutePath());
                    }

                    System.out.println("---- Triggering LLM Analysis ----");
                    coursePlanCsvPath = llamaAnalysisService.analyzeAndGenerateCoursePlan(studentInfo, snapshotPdf, progressPdf, llmConfig);
                    System.out.println("---- LLM Analysis Done ----");
                } catch (Exception llmEx) {
                    System.err.println("LLM analysis failed: " + llmEx.getMessage());
                }
            }
        } else {
            System.out.println("No valid URL resolved for snapshot; skipping PDF render.");
            debugPdfGeneration(false, 0);
        }

        // Return recommendations with CSV path
        return Map.of(
            "recommendations", List.of(),
            "summarySentence", sentence,
            "coursePlanCsvPath", coursePlanCsvPath,
            "coursePlanAvailable", !coursePlanCsvPath.isEmpty()
        );
    }

    /**
     * Generates course recommendations based on a student profile.
     * Uses the target goal major or falls back to the user's current major.
     *
     * @param profile the student profile
     * @return a map containing recommendations, summary sentence, and course plan path
     */
    @PostMapping(value = "/recommendations/profile", consumes = "application/json")
    public Map<String, Object> recommendationsForProfile(@RequestBody StudentProfile profile) {
        // Prefer target goal major; fallback to user's current major
        String major =
            profile.target() != null && profile.target().major() != null && !profile.target().major().isBlank()
                ? profile.target().major()
                : Optional.ofNullable(profile.user()).map(UserBasicInfo::major).orElse("");

        // Build info map
        Map<String, Object> info = new HashMap<>();
        info.put("graduationYear", profile.user() != null ? profile.user().graduationYear() : "");
        info.put("major", major);
        info.put("university", profile.user() != null ? profile.user().university() : "");

        String sentence = sentenceGeneration(info);
        String searchQuery = buildSearchQuery(info);
        
        // Resolve and generate PDF
        Optional<String> maybeUrl = searchService.resolveSearchUrl(searchQuery);
        String duckUrl = maybeUrl.orElse("");
        debugPrintSnapshotUrl("recommendations/profile", duckUrl);

        String coursePlanCsvPath = "";
        
        if (isValidHttpUrl(duckUrl)) {
            try {
                System.out.println("Generating PDF for degree requirements...");
                byte[] pdf = pdfService.renderExpandedPageToPdf(duckUrl);
                
                if (pdf != null && pdf.length > 0) {
                    debugPdfGeneration(true, pdf.length);
                    
                    // Find the most recent snapshot PDF
                    Path snapshotDir = Paths.get(System.getProperty("user.dir")).resolve("snapshots");
                    Path snapshotPdf = findMostRecentPdf(snapshotDir);
                    
                    // Find the uploaded progress PDF
                    Path progressPdf = null;
                    if (profile.progressFileId() != null && !profile.progressFileId().isBlank()) {
                        progressPdf = storage.resolve(profile.progressFileId());
                        System.out.println("Progress PDF found at: " + progressPdf.toAbsolutePath());
                    }
                    
                    // Call LLM with both PDFs
                    coursePlanCsvPath = llamaAnalysisService.analyzeAndGenerateCoursePlan(info, snapshotPdf, progressPdf, profile.llmConfig());
                }
            } catch (Exception ex) {
                System.err.println("PDF/LLM analysis failed: " + ex.getMessage());
                ex.printStackTrace();
                debugPdfGeneration(false, 0);
            }
        }

        // Parse uploaded progress PDF if provided
        if (profile.progressFileId() != null && !profile.progressFileId().isBlank()) {
            Path progressPdfPath = storage.resolve(profile.progressFileId());
            System.out.println("Progress PDF available at: " + progressPdfPath.toAbsolutePath());
        }

        List<String> recs = engine.generateRecommendations(major, 0.0);
        
        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recs);
        result.put("summarySentence", sentence);
        result.put("coursePlanCsvPath", coursePlanCsvPath);
        result.put("coursePlanAvailable", !coursePlanCsvPath.isEmpty());
        
        return result;
    }

    /**
     * Debug function to print all info in a given HashMap.
     *
     * @param info the student information map
     */
    public static void printStudentInfo(Map<String, Object> info) {
        System.out.println("---- Debug: Student Info ----");
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("---- End Student Info ----");
    }
    
    /**
     * Generates a sentence from student information.
     *
     * @param info the student information map
     * @return the generated sentence
     */
    public static String sentenceGeneration(Map<String, Object> info) {
        String graduationYear = "Unknown";
        String major = "Unknown";
        String university = "Unknown";
        
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            if (entry.getKey().equals("graduationYear")) {
                graduationYear = entry.getValue().toString();
            } else if (entry.getKey().equals("major")) {
                major = entry.getValue().toString();
            } else if (entry.getKey().equals("university")) {
                university = entry.getValue().toString();
            }
        }
        
        String sentence = "The " + graduationYear + " year graduate requirement of " + major + " at " + university;
        System.out.println("---- Generated Sentence ----");
        System.out.println(sentence);
        System.out.println("---- End Generated Sentence ----");
        return sentence;
    }

    /**
     * Builds a search query from student information.
     *
     * @param info the student information map
     * @return the search query string
     */
    private static String buildSearchQuery(Map<String, Object> info) {
        String year = Objects.toString(info.getOrDefault("graduationYear", ""), "").trim();
        String major = Objects.toString(info.getOrDefault("major", ""), "").trim();
        String university = Objects.toString(info.getOrDefault("university", ""), "").trim();

        List<String> parts = new ArrayList<>();
        if (!university.isEmpty()) parts.add(university);
        if (!major.isEmpty()) parts.add(major);
        parts.add("degree requirements");
        if (!year.isEmpty()) parts.add(year);

        return String.join(" ", parts);
    }

    /**
     * Finds the most recently modified PDF file in a directory.
     *
     * @param directory the directory to search
     * @return the Path to the most recent PDF, or null if none found
     */
    private Path findMostRecentPdf(Path directory) {
        try {
            if (!java.nio.file.Files.exists(directory)) {
                return null;
            }
            
            return java.nio.file.Files.list(directory)
                .filter(p -> p.toString().endsWith(".pdf"))
                .max(Comparator.comparing(p -> {
                    try {
                        return java.nio.file.Files.getLastModifiedTime(p);
                    } catch (java.io.IOException e) {
                        return null;
                    }
                }))
                .orElse(null);
        } catch (java.io.IOException e) {
            System.err.println("Error finding most recent PDF: " + e.getMessage());
            return null;
        }
    }

    /**
     * Debug printer for snapshot URLs.
     *
     * @param source the source of the URL (e.g., "recommendations")
     * @param url the URL to print
     */
    private static void debugPrintSnapshotUrl(String source, String url) {
        System.out.println("---- Debug: Snapshot URL (" + source + ") ----");
        System.out.println(url == null || url.isBlank() ? "(empty)" : url);
        System.out.println("---- End Snapshot URL ----");
    }

    /**
     * Debug function to verify PDF generation.
     *
     * @param success whether PDF generation was successful
     * @param size the size of the generated PDF in bytes
     */
    private void debugPdfGeneration(boolean success, int size) {
        System.out.println("---- Debug: PDF Generation Status ----");
        if (success) {
            System.out.println("PDF successfully generated and stored");
            System.out.println("Size: " + size + " bytes");
        } else {
            System.out.println("PDF generation failed - nothing generated");
        }
        System.out.println("---- End PDF Generation Status ----");
    }

    /**
     * Validates if a URL is a valid HTTP or HTTPS URL.
     *
     * @param url the URL to validate
     * @return true if valid HTTP/HTTPS URL, false otherwise
     */
    private static boolean isValidHttpUrl(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
