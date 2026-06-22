package com.courseguide.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Autowired;

import com.courseguide.dto.LlmConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LlamaAnalysisService {
    private static final String LLAMA_API_URL    = "http://localhost:8075/v1/chat/completions";
    private static final String LLAMA_MODELS_URL = "http://localhost:8075/v1/models";
    private static final String LLAMA_API_KEY    = System.getenv().getOrDefault("LLAMA_API_KEY", "KEY");
    private final Path csvOutputDir;

    // Tighten prompt limits
    private static final int MAX_SECTION_CHARS = 2000; // was 12000
    private static final int MAX_PROMPT_CHARS  = 6000; // was 25000

    @Autowired
    private PdfTextExtractorService pdfExtractor;

    public LlamaAnalysisService() throws IOException {
        Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
        this.csvOutputDir = workspaceRoot.resolve("course-plans");
        
        if (!Files.exists(csvOutputDir)) {
            Files.createDirectories(csvOutputDir);
        }
        
        System.out.println("---- CSV Output directory initialized ----");
        System.out.println("Location: " + csvOutputDir.toAbsolutePath());
        System.out.println("---- End ----");
    }

    public String analyzeAndGenerateCoursePlan(Map<String, Object> studentInfo, Path snapshotPdf, Path progressPdf) {
        return analyzeAndGenerateCoursePlan(studentInfo, snapshotPdf, progressPdf, null);
    }

    public String analyzeAndGenerateCoursePlan(Map<String, Object> studentInfo, Path snapshotPdf, Path progressPdf, LlmConfig config) {
        System.out.println("---- Starting LLM Analysis ----");
        
        // Extract text from both PDFs
        String degreeRequirements = extractPdfText(snapshotPdf, "Degree Requirements");
        String studentProgress = extractPdfText(progressPdf, "Student Progress");
        
        String prompt = buildPrompt(studentInfo, degreeRequirements, studentProgress);
        String llmResponse = callLlamaApi(prompt, config);
        String csvFilePath = saveCsvOutput(llmResponse, studentInfo);
        
        System.out.println("---- LLM Analysis Complete ----");
        System.out.println("CSV saved to: " + csvFilePath);
        System.out.println("---- End ----");
        
        return csvFilePath;
    }

    private String extractPdfText(Path pdfPath, String pdfType) {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            System.out.println("Warning: " + pdfType + " PDF not found at: " + pdfPath);
            return "";
        }

        try {
            System.out.println("Extracting text from " + pdfType + ": " + pdfPath.toAbsolutePath());
            String text = pdfExtractor.extractText(pdfPath);
            System.out.println(pdfType + " text extracted: " + text.length() + " characters");
            return text;
        } catch (Exception e) {
            System.err.println("Failed to extract text from " + pdfType + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private String buildPrompt(Map<String, Object> studentInfo, String degreeRequirements, String studentProgress) {
        String graduationYear = Objects.toString(studentInfo.getOrDefault("graduationYear", "Unknown"), "Unknown");
        String major = Objects.toString(studentInfo.getOrDefault("major", "Unknown"), "Unknown");
        String university = Objects.toString(studentInfo.getOrDefault("university", "Unknown"), "Unknown");

        String degTrim = trimMiddle(Objects.toString(degreeRequirements, ""), MAX_SECTION_CHARS);
        String progTrim = trimMiddle(Objects.toString(studentProgress, ""), MAX_SECTION_CHARS);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a university course planning assistant.\n\n");
        prompt.append("Student Information:\n");
        prompt.append("- University: ").append(university).append("\n");
        prompt.append("- Major: ").append(major).append("\n");
        prompt.append("- Expected Graduation Year: ").append(graduationYear).append("\n\n");

        if (!degTrim.isBlank()) {
            prompt.append("=== DEGREE REQUIREMENTS (excerpt) ===\n");
            prompt.append(degTrim).append("\n\n");
        }
        if (!progTrim.isBlank()) {
            prompt.append("=== STUDENT'S COMPLETED COURSES (excerpt) ===\n");
            prompt.append(progTrim).append("\n\n");
        }

        prompt.append("Task: Generate a complete course plan listing ALL REMAINING courses needed to graduate.\n");
        prompt.append("- Analyze the degree requirements to identify all required courses\n");
        prompt.append("- Analyze the student's progress to identify which courses are already completed\n");
        prompt.append("- Output ONLY the remaining courses that still need to be taken\n\n");

        prompt.append("Output Format Requirements:\n");
        prompt.append("1. Output ONLY CSV data, no explanations or headers\n");
        prompt.append("2. Each line represents ONE course\n");
        prompt.append("3. Order courses from MOST RECENT (top) to LAST needed (bottom)\n");
        prompt.append("4. Consider prerequisites - earlier courses must come before advanced courses\n");
        prompt.append("5. Strictly follow this CSV format:\n");
        prompt.append("   CourseName,PreqCourseName,CreditHours,Major1/Major2/GenedEdu/Minor,Description\n\n");
        prompt.append("Now generate the course plan:\n");

        String out = prompt.toString();
        if (out.length() > MAX_PROMPT_CHARS) {
            out = trimMiddle(out, MAX_PROMPT_CHARS);
        }
        // keep ~<= 3000 tokens (very rough chars/4)
        int tokens = approxTokens(out);
        if (tokens > 3000) {
            out = trimMiddle(out, 3000 * 4);
        }
        System.out.println("Prompt length (chars): " + out.length() + ", approx tokens: " + approxTokens(out));
        return out;
    }

    private static int approxTokens(String s) {
        if (s == null) return 0;
        return Math.max(1, s.length() / 4);
    }

    private static String trimMiddle(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        int keep = maxChars - 200; // leave room for marker
        int head = keep / 2;
        int tail = keep - head;
        return s.substring(0, head) + "\n\n...[TRIMMED]...\n\n" + s.substring(s.length() - tail);
    }

    // Resolve the model id from the provider's /v1/models endpoint
    private String resolveModelId(RestTemplate restTemplate, HttpHeaders headers, String modelsUrl) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(modelsUrl, HttpMethod.GET, entity, Map.class);
            Map body = resp.getBody();
            if (body != null && body.containsKey("data")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null && !data.isEmpty()) {
                    String id = Objects.toString(data.get(0).get("id"), "");
                    if (!id.isBlank()) {
                        System.out.println("Resolved model id: " + id);
                        return id;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve model id: " + e.getMessage());
        }
        return "qwen";
    }

    private String callLlamaApi(String prompt, LlmConfig config) {
        String baseUrl = (config != null && config.apiBaseUrl() != null && !config.apiBaseUrl().isBlank())
            ? config.apiBaseUrl().replaceAll("/+$", "")
            : "http://localhost:8075";

        String apiKey = (config != null && config.apiKey() != null && !config.apiKey().isBlank())
            ? config.apiKey()
            : LLAMA_API_KEY;

        String chatUrl = baseUrl + "/v1/chat/completions";
        String modelsUrl = baseUrl + "/v1/models";

        System.out.println("---- Calling LLM API ----");
        System.out.println("API URL: " + chatUrl);
        System.out.println("Sending prompt chars: " + prompt.length());
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-Api-Key", apiKey);

            String modelId;
            if (config != null && config.modelName() != null && !config.modelName().isBlank()) {
                modelId = config.modelName();
                System.out.println("Using user-provided model: " + modelId);
            } else {
                modelId = resolveModelId(restTemplate, headers, modelsUrl);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a helpful course planning assistant. Output only CSV formatted data."),
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 2048); // INCREASED from 400 to 2048
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                chatUrl, HttpMethod.POST, entity, Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = message == null ? null : (String) message.get("content");
                    
                    // DEBUG: Print the actual LLM response
                    System.out.println("==================================================");
                    System.out.println("     RAW LLM RESPONSE");
                    System.out.println("==================================================");
                    System.out.println(content);
                    System.out.println("==================================================");
                    System.out.println("LLM content length: " + (content != null ? content.length() : 0));
                    
                    return content == null ? "" : content;
                }
            }
            
            System.err.println("WARNING: Response body structure unexpected");
            System.err.println("Response body: " + responseBody);
            throw new RuntimeException("Invalid response from Llama API");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            System.err.println("---- HTTP Error Response ----");
            System.err.println("Status Code: " + e.getStatusCode());
            System.err.println("Response Body: " + body);
            if (body != null && body.contains("exceed_context_size_error")) {
                System.err.println("Context too large. Skipping LLM analysis and continuing.");
                return "";
            }
            throw new RuntimeException("Llama API error: " + e.getStatusCode() + " - " + body, e);
        } catch (Exception e) {
            System.err.println("---- Llama API Call Failed ----");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to call Llama API: " + e.getMessage(), e);
        }
    }

    private String saveCsvOutput(String llmResponse, Map<String, Object> studentInfo) {
        try {
            System.out.println("---- Processing LLM Response for CSV ----");
            System.out.println("Raw response length: " + (llmResponse != null ? llmResponse.length() : 0));
            
            // Clean up the response to extract only CSV content
            String csvContent = extractCsvFromResponse(llmResponse);
            
            System.out.println("Extracted CSV content length: " + csvContent.length());
            System.out.println("Extracted CSV content:\n" + csvContent);
            
            if (csvContent.isEmpty() || csvContent.isBlank()) {
                System.err.println("WARNING: LLM returned no CSV content!");
                System.err.println("Original response: " + llmResponse);
                // Return empty path but don't throw exception
                return "";
            }
            
            // Add CSV header
            String csvWithHeader = "CourseName,PreqCourseName,CreditHours,Category,Description\n" + csvContent;
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmms"));
            String major = Objects.toString(studentInfo.getOrDefault("major", "Unknown"), "Unknown")
                .replaceAll("[^a-zA-Z0-9]", "_");
            String filename = "course_plan_" + major + "_" + timestamp + ".csv";
            
            Path csvFile = csvOutputDir.resolve(filename);
            Files.writeString(csvFile, csvWithHeader);
            
            System.out.println("==================================================");
            System.out.println("     COURSE PLAN CSV SAVED SUCCESSFULLY");
            System.out.println("==================================================");
            System.out.println("Filename:     " + filename);
            System.out.println("Full Path:    " + csvFile.toAbsolutePath());
            System.out.println("Size:         " + csvWithHeader.length() + " bytes");
            System.out.println("Lines:        " + (csvContent.split("\n").length + 1));
            System.out.println("==================================================");
            
            return csvFile.toAbsolutePath().toString();
            
        } catch (IOException e) {
            System.err.println("Failed to save CSV: " + e.getMessage());
            throw new RuntimeException("Failed to save CSV output", e);
        }
    }

    private String extractCsvFromResponse(String response) {
        // Remove any markdown code blocks
        String cleaned = response.replaceAll("```csv\\s*", "")
                                .replaceAll("```\\s*", "")
                                .trim();
        
        // Split into lines and filter out any non-CSV lines
        String[] lines = cleaned.split("\n");
        StringBuilder csvBuilder = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            // Check if line looks like CSV (has commas and content)
            if (!line.isEmpty() && line.contains(",") && !line.startsWith("#")) {
                // Skip header lines that might have been included
                if (!line.toLowerCase().startsWith("coursename,")) {
                    csvBuilder.append(line).append("\n");
                }
            }
        }
        
        return csvBuilder.toString().trim();
    }
}