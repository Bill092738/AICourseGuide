package com.courseguide.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service for interacting with Supabase for online progress storage.
 * Falls back to local SQL storage when Supabase is unavailable.
 */
@Service
public class SupabaseService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.anon-key:}")
    private String supabaseAnonKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Checks if Supabase is configured.
     *
     * @return true if Supabase URL and key are configured
     */
    public boolean isConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank() 
            && supabaseAnonKey != null && !supabaseAnonKey.isBlank();
    }

    /**
     * Saves user progress to Supabase.
     *
     * @param progress the user progress to save
     * @return the case number
     * @throws RuntimeException if Supabase is not configured or request fails
     */
    public String saveProgress(UserProgressService.UserProgress progress) {
        if (!isConfigured()) {
            throw new RuntimeException("Supabase is not configured");
        }

        try {
            String url = supabaseUrl + "/rest/v1/user_progress";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", supabaseAnonKey);
            headers.set("Authorization", "Bearer " + supabaseAnonKey);
            headers.set("Prefer", "resolution=merge-duplicates");

            Map<String, Object> body = new HashMap<>();
            body.put("case_number", progress.getCaseNumber());
            body.put("form_data", progress.getFormData());
            body.put("xml_content", progress.getXmlContent());
            body.put("selected_courses", progress.getSelectedCourses());
            body.put("total_credits", progress.getTotalCredits());
            body.put("synced_to_cloud", true);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return progress.getCaseNumber();
            } else {
                throw new RuntimeException("Supabase returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save to Supabase: " + e.getMessage(), e);
        }
    }

    /**
     * Loads user progress from Supabase.
     *
     * @param caseNumber the case number to load
     * @return the user progress, or null if not found
     * @throws RuntimeException if Supabase is not configured
     */
    public UserProgressService.UserProgress loadProgress(String caseNumber) {
        if (!isConfigured()) {
            throw new RuntimeException("Supabase is not configured");
        }

        try {
            String url = supabaseUrl + "/rest/v1/user_progress?case_number=eq." + caseNumber;

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.set("Authorization", "Bearer " + supabaseAnonKey);
            headers.set("Accept", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, List.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = response.getBody();
                if (!results.isEmpty()) {
                    Map<String, Object> row = results.get(0);
                    UserProgressService.UserProgress progress = new UserProgressService.UserProgress();
                    progress.setCaseNumber((String) row.get("case_number"));
                    progress.setFormData((String) row.get("form_data"));
                    progress.setXmlContent((String) row.get("xml_content"));
                    progress.setSelectedCourses((String) row.get("selected_courses"));
                    progress.setTotalCredits((String) row.get("total_credits"));
                    progress.setCreatedAt((String) row.get("created_at"));
                    progress.setUpdatedAt((String) row.get("updated_at"));
                    progress.setSyncedToCloud(true);
                    return progress;
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load from Supabase: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes user progress from Supabase.
     *
     * @param caseNumber the case number to delete
     * @return true if deleted
     * @throws RuntimeException if Supabase is not configured
     */
    public boolean deleteProgress(String caseNumber) {
        if (!isConfigured()) {
            throw new RuntimeException("Supabase is not configured");
        }

        try {
            String url = supabaseUrl + "/rest/v1/user_progress?case_number=eq." + caseNumber;

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseAnonKey);
            headers.set("Authorization", "Bearer " + supabaseAnonKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.DELETE, entity, String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete from Supabase: " + e.getMessage(), e);
        }
    }
}
