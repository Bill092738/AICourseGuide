package com.courseguide.services;

import com.courseguide.models.Course;
import com.courseguide.utils.XMLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Imports LLM-generated XML into MySQL, tolerating row errors.
 * Creates tables if missing, clears previous data, inserts courses and prerequisites.
 */
@Service
public class XmlImportService {

    @Autowired
    private JdbcTemplate jdbc;

    public static class ImportResult {
        private int successCount;
        private final List<String> errors = new ArrayList<>();

        public int getSuccessCount() { return successCount; }
        public List<String> getErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }

        private void incSuccess() { successCount++; }
        private void addError(String e) { errors.add(e); }
    }

    public ImportResult importCoursePlan(String xmlPath) {
        ImportResult result = new ImportResult();
        try {
            if (xmlPath == null || xmlPath.isBlank() || !Files.exists(Path.of(xmlPath))) {
                result.addError("XML path missing or not found: " + xmlPath);
                return result;
            }

            ensureTables();

            // Clear old data (fresh import)
            jdbc.update("DELETE FROM prerequisites");
            jdbc.update("DELETE FROM courses");

            List<Course> rows = XMLUtils.parseCoursesFromXML(xmlPath);

            for (Course r : rows) {
                String courseName = safe(r.getCourseName());
                if (courseName.isBlank()) {
                    result.addError("Skipping row due to empty CourseName");
                    continue;
                }
                int credit = r.getCreditHours() <= 0 ? 3 : r.getCreditHours();
                String category = normalizeCategory(r.getCategory());
                String desc = safe(r.getDescription());

                upsertCourse(courseName, credit, category, desc, result);
                result.incSuccess();

                // Parse multi-prereqs on ; / or ,
                for (String pre : splitPrereqs(r.getPreqCourseName())) {
                    if (pre.isBlank()) continue;
                    upsertCourse(pre, 3, "Major1", "Prerequisite course", result);
                    insertPrereqEdge(courseName, pre, 0, result); // AND=0 default
                }
            }
        } catch (Exception ex) {
            result.addError("Import failed: " + ex.getMessage());
        }
        return result;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private void ensureTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS courses (
                id INT AUTO_INCREMENT PRIMARY KEY,
                course_name VARCHAR(100) NOT NULL UNIQUE,
                credit_hours INT NOT NULL DEFAULT 3,
                category VARCHAR(32) NOT NULL DEFAULT 'Major1',
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_course_name (course_name),
                INDEX idx_category (category)
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS prerequisites (
                id INT AUTO_INCREMENT PRIMARY KEY,
                dependent_course_id INT NOT NULL,
                prerequisite_course_id INT NOT NULL,
                dependency_flag TINYINT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (dependent_course_id) REFERENCES courses(id) ON DELETE CASCADE,
                FOREIGN KEY (prerequisite_course_id) REFERENCES courses(id) ON DELETE CASCADE,
                INDEX idx_dependent (dependent_course_id),
                INDEX idx_prerequisite (prerequisite_course_id),
                INDEX idx_dependency_flag (dependency_flag),
                UNIQUE KEY unique_prerequisite (dependent_course_id, prerequisite_course_id)
            )
        """);
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "Major1";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("m1") || s.equals("major1") || s.equals("cis")) return "Major1";
        if (s.equals("m2") || s.equals("major2")) return "Major2";
        if (s.startsWith("gen")) return "GenedEdu";
        if (s.startsWith("minor")) return "Minor";
        return raw;
    }

    private List<String> splitPrereqs(String preq) {
        if (preq == null || preq.isBlank()) return List.of();
        return Arrays.stream(preq.split("[;/,]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    private void upsertCourse(String name, int credit, String category, String desc, ImportResult result) {
        try {
            jdbc.update("""
                INSERT INTO courses (course_name, credit_hours, category, description)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    credit_hours = VALUES(credit_hours),
                    category = VALUES(category),
                    description = CASE WHEN (description IS NULL OR description = '') THEN VALUES(description) ELSE description END
            """, name, credit, category, desc);
        } catch (Exception e) {
            result.addError("Failed upsert course '" + name + "': " + e.getMessage());
        }
    }

    private void insertPrereqEdge(String course, String prereq, int flag, ImportResult result) {
        try {
            Integer dcId = jdbc.queryForObject("SELECT id FROM courses WHERE course_name = ?", Integer.class, course);
            Integer pcId = jdbc.queryForObject("SELECT id FROM courses WHERE course_name = ?", Integer.class, prereq);
            if (dcId == null || pcId == null) {
                result.addError("Missing IDs for edge " + course + " <- " + prereq);
                return;
            }
            jdbc.update("""
                INSERT IGNORE INTO prerequisites (dependent_course_id, prerequisite_course_id, dependency_flag)
                VALUES (?,?,?)
            """, dcId, pcId, flag);
        } catch (Exception e) {
            result.addError("Failed insert edge " + course + " <- " + prereq + ": " + e.getMessage());
        }
    }
}
