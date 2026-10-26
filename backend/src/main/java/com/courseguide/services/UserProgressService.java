package com.courseguide.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for managing user progress with case numbers.
 * Supports both local (SQL) and online (Supabase) storage modes.
 */
@Service
public class UserProgressService {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SupabaseService supabaseService;

    private static final String CASE_NUMBER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CASE_NUMBER_LENGTH = 8;
    private static final Random RANDOM = new Random();

    /**
     * Data class representing a saved user progress session.
     */
    public static class UserProgress {
        private String caseNumber;
        private String formData;
        private String xmlContent;
        private String selectedCourses;
        private String totalCredits;
        private String createdAt;
        private String updatedAt;
        private boolean syncedToCloud;

        public UserProgress() {}

        public UserProgress(String caseNumber, String formData, String xmlContent, 
                           String selectedCourses, String totalCredits) {
            this.caseNumber = caseNumber;
            this.formData = formData;
            this.xmlContent = xmlContent;
            this.selectedCourses = selectedCourses;
            this.totalCredits = totalCredits;
            this.syncedToCloud = false;
        }

        // Getters and setters
        public String getCaseNumber() { return caseNumber; }
        public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }
        public String getFormData() { return formData; }
        public void setFormData(String formData) { this.formData = formData; }
        public String getXmlContent() { return xmlContent; }
        public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
        public String getSelectedCourses() { return selectedCourses; }
        public void setSelectedCourses(String selectedCourses) { this.selectedCourses = selectedCourses; }
        public String getTotalCredits() { return totalCredits; }
        public void setTotalCredits(String totalCredits) { this.totalCredits = totalCredits; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        public boolean isSyncedToCloud() { return syncedToCloud; }
        public void setSyncedToCloud(boolean syncedToCloud) { this.syncedToCloud = syncedToCloud; }
    }

    /**
     * Initializes the user_progress table if it doesn't exist.
     */
    public void initializeTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS user_progress (
                id INT AUTO_INCREMENT PRIMARY KEY,
                case_number VARCHAR(10) NOT NULL UNIQUE,
                form_data TEXT,
                xml_content TEXT,
                selected_courses TEXT,
                total_credits VARCHAR(10),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                synced_to_cloud BOOLEAN DEFAULT FALSE,
                INDEX idx_case_number (case_number)
            )
        """);
    }

    /**
     * Generates a unique case number.
     *
     * @return a unique case number (uppercase letters + digits)
     */
    public String generateCaseNumber() {
        String caseNumber;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CASE_NUMBER_LENGTH; i++) {
                sb.append(CASE_NUMBER_CHARS.charAt(RANDOM.nextInt(CASE_NUMBER_CHARS.length())));
            }
            caseNumber = sb.toString();
        } while (caseNumberExists(caseNumber));
        return caseNumber;
    }

    /**
     * Checks if a case number already exists in the database.
     *
     * @param caseNumber the case number to check
     * @return true if exists, false otherwise
     */
    private boolean caseNumberExists(String caseNumber) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_progress WHERE case_number = ?",
            Integer.class,
            caseNumber
        );
        return count != null && count > 0;
    }

    /**
     * Saves user progress to local SQL database.
     *
     * @param progress the user progress to save
     * @return the case number of the saved progress
     */
    public String saveProgressLocal(UserProgress progress) {
        initializeTable();
        
        if (progress.getCaseNumber() == null || progress.getCaseNumber().isEmpty()) {
            progress.setCaseNumber(generateCaseNumber());
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        progress.setUpdatedAt(now);
        if (progress.getCreatedAt() == null) {
            progress.setCreatedAt(now);
        }

        try {
            jdbc.update("""
                INSERT INTO user_progress (case_number, form_data, xml_content, selected_courses, total_credits, created_at, updated_at, synced_to_cloud)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    form_data = VALUES(form_data),
                    xml_content = VALUES(xml_content),
                    selected_courses = VALUES(selected_courses),
                    total_credits = VALUES(total_credits),
                    updated_at = VALUES(updated_at),
                    synced_to_cloud = FALSE
            """,
                progress.getCaseNumber(),
                progress.getFormData(),
                progress.getXmlContent(),
                progress.getSelectedCourses(),
                progress.getTotalCredits(),
                progress.getCreatedAt(),
                progress.getUpdatedAt(),
                progress.isSyncedToCloud()
            );
            return progress.getCaseNumber();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save progress locally: " + e.getMessage(), e);
        }
    }

    /**
     * Loads user progress from local SQL database.
     *
     * @param caseNumber the case number to load
     * @return the user progress, or null if not found
     */
    public UserProgress loadProgressLocal(String caseNumber) {
        initializeTable();
        
        try {
            return jdbc.queryForObject(
                "SELECT * FROM user_progress WHERE case_number = ?",
                (rs, rowNum) -> {
                    UserProgress progress = new UserProgress();
                    progress.setCaseNumber(rs.getString("case_number"));
                    progress.setFormData(rs.getString("form_data"));
                    progress.setXmlContent(rs.getString("xml_content"));
                    progress.setSelectedCourses(rs.getString("selected_courses"));
                    progress.setTotalCredits(rs.getString("total_credits"));
                    progress.setCreatedAt(rs.getString("created_at"));
                    progress.setUpdatedAt(rs.getString("updated_at"));
                    progress.setSyncedToCloud(rs.getBoolean("synced_to_cloud"));
                    return progress;
                },
                caseNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lists all saved progress entries.
     *
     * @return list of user progress summaries
     */
    public List<UserProgress> listAllProgressLocal() {
        initializeTable();
        
        try {
            return jdbc.query(
                "SELECT * FROM user_progress ORDER BY updated_at DESC",
                (rs, rowNum) -> {
                    UserProgress progress = new UserProgress();
                    progress.setCaseNumber(rs.getString("case_number"));
                    progress.setFormData(rs.getString("form_data"));
                    progress.setCreatedAt(rs.getString("created_at"));
                    progress.setUpdatedAt(rs.getString("updated_at"));
                    progress.setSyncedToCloud(rs.getBoolean("synced_to_cloud"));
                    return progress;
                }
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Deletes user progress from local database.
     *
     * @param caseNumber the case number to delete
     * @return true if deleted, false otherwise
     */
    public boolean deleteProgressLocal(String caseNumber) {
        try {
            int rows = jdbc.update("DELETE FROM user_progress WHERE case_number = ?", caseNumber);
            return rows > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Saves user progress to online storage (Supabase) with SQL fallback.
     *
     * @param progress the user progress to save
     * @return the case number of the saved progress
     */
    public String saveProgressOnline(UserProgress progress) {
        try {
            return supabaseService.saveProgress(progress);
        } catch (Exception e) {
            System.err.println("Online save failed, falling back to local: " + e.getMessage());
            String caseNumber = saveProgressLocal(progress);
            markAsNotSynced(caseNumber);
            return caseNumber;
        }
    }

    /**
     * Loads user progress from online storage (Supabase) with SQL fallback.
     *
     * @param caseNumber the case number to load
     * @return the user progress, or null if not found
     */
    public UserProgress loadProgressOnline(String caseNumber) {
        try {
            UserProgress progress = supabaseService.loadProgress(caseNumber);
            if (progress != null) {
                return progress;
            }
        } catch (Exception e) {
            System.err.println("Online load failed, falling back to local: " + e.getMessage());
        }
        return loadProgressLocal(caseNumber);
    }

    /**
     * Marks a progress entry as not synced to cloud.
     *
     * @param caseNumber the case number to mark
     */
    private void markAsNotSynced(String caseNumber) {
        jdbc.update("UPDATE user_progress SET synced_to_cloud = FALSE WHERE case_number = ?", caseNumber);
    }

    /**
     * Marks a progress entry as synced to cloud.
     *
     * @param caseNumber the case number to mark
     */
    public void markAsSynced(String caseNumber) {
        jdbc.update("UPDATE user_progress SET synced_to_cloud = TRUE WHERE case_number = ?", caseNumber);
    }

    /**
     * Syncs all unsynced local progress to online storage.
     *
     * @return number of records synced
     */
    public int syncLocalToOnline() {
        List<UserProgress> unsynced = jdbc.query(
            "SELECT * FROM user_progress WHERE synced_to_cloud = FALSE",
            (rs, rowNum) -> {
                UserProgress progress = new UserProgress();
                progress.setCaseNumber(rs.getString("case_number"));
                progress.setFormData(rs.getString("form_data"));
                progress.setXmlContent(rs.getString("xml_content"));
                progress.setSelectedCourses(rs.getString("selected_courses"));
                progress.setTotalCredits(rs.getString("total_credits"));
                progress.setCreatedAt(rs.getString("created_at"));
                progress.setUpdatedAt(rs.getString("updated_at"));
                progress.setSyncedToCloud(false);
                return progress;
            }
        );

        int syncedCount = 0;
        for (UserProgress progress : unsynced) {
            try {
                supabaseService.saveProgress(progress);
                markAsSynced(progress.getCaseNumber());
                syncedCount++;
            } catch (Exception e) {
                System.err.println("Failed to sync " + progress.getCaseNumber() + ": " + e.getMessage());
            }
        }
        return syncedCount;
    }

    /**
     * Gets count of unsynced records.
     *
     * @return number of unsynced records
     */
    public int getUnsyncedCount() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_progress WHERE synced_to_cloud = FALSE",
            Integer.class
        );
        return count != null ? count : 0;
    }
}
