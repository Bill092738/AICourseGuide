package com.courseguide;

import com.courseguide.dto.StudentProfile;
import com.courseguide.dto.UserBasicInfo;
import com.courseguide.processors.RecommendationEngine;
import com.courseguide.services.FileStorageService;
import com.courseguide.services.WebPagePdfService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RecommendationEngine engine = new RecommendationEngine();

    @Autowired
    private FileStorageService storage;

    @Autowired
    private WebPagePdfService pdfService;

    // Public student info object for storing received data
    public static Map<String, Object> studentInfo = new HashMap<>();

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // Updated: Receive all user info and store in public studentInfo object
    @PostMapping("/recommendations")
    public Map<String, List<String>> recommendations(@RequestBody Map<String, Object> body) {
        // Store all received fields in the public studentInfo object
        studentInfo.clear();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            studentInfo.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
        }

        // Call debug function to print all info
        printStudentInfo(studentInfo);
        sentenseGeneration(studentInfo);

        // Placeholder: return empty recommendations for now
        return Map.of("recommendations", List.of());
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

    // Debug function to print all info in a given HashMap
    public static void printStudentInfo(Map<String, Object> info) {
        System.out.println("---- Debug: Student Info ----");
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("---- End Student Info ----");
    }
    
    // generate a sentence for student info
    // Here will have a method called Sentence Generator which will generate sentences based on user input stored in studentInfo
    public static void sentenceGeneration(Map<String, Object> info) {
        System.out.println("---- Generated Sentence ----");
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
        
        String sentence = "The " + graduationYear + " year graduate requirement of " + major + " at " + university ;
        
        System.out.println(sentence);
        System.out.println("---- End Generated Sentence ----");
    }

    // Function to generate PDF snapshot of a given URL
    @GetMapping(value = "/snapshot/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdf(@RequestParam String url) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required");
        }
        debugPrintSnapshotUrl("direct", url);
        byte[] pdf = pdfService.renderExpandedPageToPdf(url);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"snapshot.pdf\"")
            .body(pdf);
    }

    // Debug printer for snapshot URLs
    private static void debugPrintSnapshotUrl(String source, String url) {
        System.out.println("---- Debug: Snapshot URL (" + source + ") ----");
        System.out.println(url);
        System.out.println("---- End Snapshot URL ----");
    }

    // Helper: resolve the first DuckDuckGo result URL for a query
    private Optional<String> resolveFirstDuckDuckGoUrl(String query) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("RelatedTopics")) {
                List<?> topics = (List<?>) response.get("RelatedTopics");
                for (Object t : topics) {
                    if (t instanceof Map<?, ?> topic) {
                        Object firstUrl = topic.get("FirstURL");
                        if (firstUrl != null) return Optional.of(firstUrl.toString());
                        Object sub = topic.get("Topics");
                        if (sub instanceof List<?> subList) {
                            for (Object s : subList) {
                                if (s instanceof Map<?, ?> subTopic) {
                                    Object nestedUrl = subTopic.get("FirstURL");
                                    if (nestedUrl != null) return Optional.of(nestedUrl.toString());
                                }
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Generate PDF from the first DuckDuckGo search result for the given query
    @GetMapping(value = "/snapshot/pdf/search", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdfFromSearch(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        String resolvedUrl = resolveFirstDuckDuckGoUrl(query)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No DuckDuckGo result URL found"));
        debugPrintSnapshotUrl("duckduckgo", resolvedUrl);
        byte[] pdf = pdfService.renderExpandedPageToPdf(resolvedUrl);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"snapshot.pdf\"")
            .body(pdf);
    }
}