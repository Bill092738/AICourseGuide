package com.courseguide.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LlamaAnalysisService {

    private static final String LLAMA_API_URL = "http://localhost:8075/v1/chat/completions";
    private static final String LLAMA_API_KEY = "KEY";
    private final Path csvOutputDir;

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
        System.out.println("---- Starting LLM Analysis ----");
        
        // Extract text from both PDFs
        String degreeRequirements = extractPdfText(snapshotPdf, "Degree Requirements");
        String studentProgress = extractPdfText(progressPdf, "Student Progress");
        
        String prompt = buildPrompt(studentInfo, degreeRequirements, studentProgress);
        String llmResponse = callLlamaApi(prompt);
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
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a university course planning assistant.\n\n");
        prompt.append("Student Information:\n");
        prompt.append("- University: ").append(university).append("\n");
        prompt.append("- Major: ").append(major).append("\n");
        prompt.append("- Expected Graduation Year: ").append(graduationYear).append("\n\n");
        
        if (degreeRequirements != null && !degreeRequirements.isBlank()) {
            prompt.append("=== DEGREE REQUIREMENTS (from university website) ===\n");
            prompt.append(degreeRequirements).append("\n\n");
        }
        
        if (studentProgress != null && !studentProgress.isBlank()) {
            prompt.append("=== STUDENT'S COMPLETED COURSES (from uploaded transcript/progress report) ===\n");
            prompt.append(studentProgress).append("\n\n");
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
        prompt.append("Field Details:\n");
        prompt.append("- CourseName: Full course code and name (e.g., CS 101 Intro to Programming)\n");
        prompt.append("- PreqCourseName: Prerequisite course name or 'None'\n");
        prompt.append("- CreditHours: Number (e.g., 3, 4)\n");
        prompt.append("- Major1/Major2/GenedEdu/Minor: Category of the course\n");
        prompt.append("- Description: Brief description (keep under 100 chars, no commas)\n\n");
        prompt.append("Example Output:\n");
        prompt.append("CS 401 Advanced Algorithms,CS 301 Data Structures,3,Major1,Study of advanced algorithm design and analysis\n");
        prompt.append("CS 301 Data Structures,CS 201 Programming II,4,Major1,Implementation of fundamental data structures\n\n");
        prompt.append("Now generate the course plan:\n");
        
        return prompt.toString();
    }

    private String callLlamaApi(String prompt) {
        System.out.println("---- Calling Llama API ----");
        System.out.println("API URL: " + LLAMA_API_URL);
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            Map<String, Object> requestBody = new HashMap<>();
            // Try using the actual model filename or just "gpt-3.5-turbo" as a generic name
            requestBody.put("model", "gpt-3.5-turbo"); // llama-server often accepts this generic name
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a helpful course planning assistant. Output only CSV formatted data."),
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 4096);
            requestBody.put("stream", false); // Explicitly disable streaming
            
            System.out.println("---- Request Body ----");
            System.out.println(requestBody);
            System.out.println("---- End Request Body ----");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + LLAMA_API_KEY);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                LLAMA_API_URL,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    
                    System.out.println("---- LLM Response Received ----");
                    System.out.println("Length: " + content.length() + " characters");
                    System.out.println("---- End Response ----");
                    
                    return content;
                }
            }
            
            throw new RuntimeException("Invalid response from Llama API");
            
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("---- HTTP Error Response ----");
            System.err.println("Status Code: " + e.getStatusCode());
            System.err.println("Status Text: " + e.getStatusText());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
            System.err.println("---- End HTTP Error ----");
            throw new RuntimeException("Llama API returned error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            System.err.println("---- Llama API Call Failed ----");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("---- End Error ----");
            throw new RuntimeException("Failed to call Llama API: " + e.getMessage(), e);
        }
    }

    private String saveCsvOutput(String llmResponse, Map<String, Object> studentInfo) {
        try {
            // Clean up the response to extract only CSV content
            String csvContent = extractCsvFromResponse(llmResponse);
            
            // Add CSV header
            String csvWithHeader = "CourseName,PreqCourseName,CreditHours,Category,Description\n" + csvContent;
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
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