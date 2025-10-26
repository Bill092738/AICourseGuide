package com.courseguide;

import com.courseguide.dto.StudentProfile;
import com.courseguide.dto.UserBasicInfo;
import com.courseguide.processors.RecommendationEngine;
import com.courseguide.services.FileStorageService;
import com.courseguide.services.WebPagePdfService;
import com.courseguide.services.LlamaAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

// HTML fallback
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URLDecoder;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RecommendationEngine engine = new RecommendationEngine();

    @Autowired
    private FileStorageService storage;

    @Autowired
    private WebPagePdfService pdfService;

    @Autowired
    private LlamaAnalysisService llamaAnalysisService;

    // Public student info object for storing received data
    public static Map<String, Object> studentInfo = new HashMap<>();

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    // Updated: Receive all user info and store in public studentInfo object
    @PostMapping("/recommendations")
    public Map<String, Object> recommendations(@RequestBody Map<String, Object> body) {
        // Store all received fields in the public studentInfo object
        studentInfo.clear();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            studentInfo.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
        }

        // Call debug function to print all info
        printStudentInfo(studentInfo);
        String sentence = sentenceGeneration(studentInfo);

        // Build a search-friendly query instead of using the human sentence
        String searchQuery = buildSearchQuery(studentInfo);

        // Call DuckDuckGo to resolve a URL for the built query (with HTML fallback)
        Optional<String> maybeUrl = resolveSearchUrl(searchQuery);
        String duckUrl = maybeUrl.orElse("");
        debugPrintSnapshotUrl("recommendations", duckUrl);

        String coursePlanCsvPath = "";
        
        // Only render if we have a valid http(s) URL; otherwise skip silently
        if (isValidHttpUrl(duckUrl)) {
            try {
                System.out.println("Attempting to generate PDF for: " + duckUrl);
                byte[] pdf = pdfService.renderExpandedPageToPdf(duckUrl);
                boolean success = pdf != null && pdf.length > 0;
                debugPdfGeneration(success, pdf != null ? pdf.length : 0);
                
                if (success) {
                    // Find the most recent snapshot PDF
                    Path snapshotDir = Paths.get(System.getProperty("user.dir")).resolve("snapshots");
                    Path snapshotPdf = findMostRecentPdf(snapshotDir);
                    
                    // Find the uploaded progress PDF
                    Path progressPdf = null;
                    String progressFileId = Objects.toString(studentInfo.get("progressFileId"), "");
                    if (!progressFileId.isEmpty()) {
                        progressPdf = storage.resolve(progressFileId);
                        System.out.println("Progress PDF found at: " + progressPdf.toAbsolutePath());
                    }
                    
                    // Call LLM with both PDFs
                    System.out.println("---- Triggering LLM Analysis ----");
                    coursePlanCsvPath = llamaAnalysisService.analyzeAndGenerateCoursePlan(studentInfo, snapshotPdf, progressPdf);
                    System.out.println("---- LLM Analysis Triggered ----");
                }
            } catch (Exception ex) {
                System.err.println("---- Snapshot PDF render failed ----");
                System.err.println("Exception: " + ex.getClass().getName());
                System.err.println("Message: " + ex.getMessage());
                ex.printStackTrace();
                System.err.println("---- End exception ----");
                debugPdfGeneration(false, 0);
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

    // Updated: After PDF generation, analyze with LLM and generate course plan CSV
    @PostMapping(value = "/recommendations/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
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
        Optional<String> maybeUrl = resolveSearchUrl(searchQuery);
        String duckUrl = maybeUrl.orElse("");
        debugPrintSnapshotUrl("recommendations/profile", duckUrl);

        String pdfSummary = "";
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
                    coursePlanCsvPath = llamaAnalysisService.analyzeAndGenerateCoursePlan(info, snapshotPdf, progressPdf);
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
            // TODO: Parse this PDF to extract completed courses
        }

        List<String> recs = engine.generateRecommendations(major, 0.0);
        
        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recs);
        result.put("summarySentence", sentence);
        result.put("coursePlanCsvPath", coursePlanCsvPath);
        result.put("coursePlanAvailable", !coursePlanCsvPath.isEmpty());
        
        return result;
    }

    private Path findMostRecentPdf(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return null;
        }
        
        return Files.list(directory)
            .filter(p -> p.toString().endsWith(".pdf"))
            .max(Comparator.comparing(p -> {
                try {
                    return Files.getLastModifiedTime(p);
                } catch (IOException e) {
                    return null;
                }
            }))
            .orElse(null);
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
    // Returns the generated sentence instead of printing only
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

    // Function to generate PDF snapshot of a given URL
    @GetMapping(value = "/snapshot/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdf(@RequestParam String url) {
        if (url == null || url.isBlank() || !isValidHttpUrl(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valid http(s) url is required");
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
        System.out.println(url == null || url.isBlank() ? "(empty)" : url);
        System.out.println("---- End Snapshot URL ----");
    }

    // Build a query that works better with DuckDuckGo Instant Answer API
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

    // Aggregate resolver: try Instant Answer JSON first, then HTML results page
    public Optional<String> resolveSearchUrl(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        Optional<String> viaIa = resolveFirstDuckDuckGoUrl(query);
        if (viaIa.isPresent()) return viaIa;
        return resolveFirstDuckDuckGoHtmlResult(query);
    }

    // Helper: resolve the first DuckDuckGo result URL for a query (Instant Answer JSON)
    public Optional<String> resolveFirstDuckDuckGoUrl(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&no_redirect=1&skip_disambig=1";

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> response = resp.getBody();
            if (response == null) return Optional.empty();

            Object redirect = response.get("Redirect");
            if (redirect instanceof String s && isValidHttpUrl(s) && !s.isBlank()) return Optional.of(s);

            Object abstractUrl = response.get("AbstractURL");
            if (abstractUrl instanceof String s && isValidHttpUrl(s) && !s.isBlank()) return Optional.of(s);

            Object results = response.get("Results");
            if (results instanceof List<?> resList) {
                for (Object r : resList) {
                    if (r instanceof Map<?, ?> res) {
                        Object first = res.get("FirstURL");
                        if (first instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                    }
                }
            }

            Object related = response.get("RelatedTopics");
            if (related instanceof List<?> topics) {
                for (Object t : topics) {
                    if (t instanceof Map<?, ?> topic) {
                        Object firstUrl = topic.get("FirstURL");
                        if (firstUrl instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                        Object sub = topic.get("Topics");
                        if (sub instanceof List<?> subList) {
                            for (Object sObj : subList) {
                                if (sObj instanceof Map<?, ?> subTopic) {
                                    Object nestedUrl = subTopic.get("FirstURL");
                                    if (nestedUrl instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                                }
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("DuckDuckGo IA resolve failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    // Fallback: fetch HTML results page and parse the first result URL
    private Optional<String> resolveFirstDuckDuckGoHtmlResult(String query) {
        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36")
                .referrer("https://duckduckgo.com/")
                .timeout(8000)
                .get();

            // Typical selector for result anchors on the HTML page
            for (Element a : doc.select("a.result__a[href], a.result__url[href]")) {
                String href = a.attr("href");
                String real = decodeDuckDuckGoRedirect(href);
                if (isValidHttpUrl(real)) return Optional.of(real);
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("DuckDuckGo HTML resolve failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    // Decode /l/?...uddg=... redirect links to the real target URL
    private static String decodeDuckDuckGoRedirect(String href) {
        if (href == null || href.isBlank()) return "";
        try {
            // https://duckduckgo.com/l/?kh=-1&uddg=<encodedTarget>
            int q = href.indexOf('?');
            if (q >= 0 && href.contains("uddg=")) {
                String qs = href.substring(q + 1);
                for (String part : qs.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String k = part.substring(0, eq);
                        String v = part.substring(eq + 1);
                        if ("uddg".equals(k)) {
                            return URLDecoder.decode(v, StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            return href;
        } catch (Exception ex) {
            return "";
        }
    }

    // Generate PDF from the first DuckDuckGo search result for the given query
    @GetMapping(value = "/snapshot/pdf/search", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdfFromSearch(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        String resolvedUrl = resolveSearchUrl(query)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No DuckDuckGo result URL found"));
        if (!isValidHttpUrl(resolvedUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolved URL is not http(s)");
        }
        debugPrintSnapshotUrl("duckduckgo", resolvedUrl);
        byte[] pdf = pdfService.renderExpandedPageToPdf(resolvedUrl);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"snapshot.pdf\"")
            .body(pdf);
    }

    // Debug function to verify PDF generation
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

    // Helper to validate http(s) URLs
    private static boolean isValidHttpUrl(String url) {
        try {
            URI u = URI.create(url);
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}