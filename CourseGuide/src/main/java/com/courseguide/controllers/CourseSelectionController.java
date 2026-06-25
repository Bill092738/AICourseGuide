package com.courseguide.controllers;

import com.courseguide.services.CsvImportService;
import com.courseguide.services.CourseSelectionService;
import com.courseguide.services.SimpleCsvSelectionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Controller for course selection endpoints.
 * Handles course selection logic with SQL and CSV fallback.
 */
@RestController
@RequestMapping("/api")
public class CourseSelectionController {

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private CourseSelectionService courseSelectionService;

    @Autowired
    private SimpleCsvSelectionService simpleCsvSelectionService;

    /**
     * Selects courses based on the provided course plan CSV and constraints.
     * Tries DB-backed path first, then falls back to CSV-only selection.
     *
     * @param body the request body containing coursePlanCsvPath, maxCredits, and completedCourses
     * @return a map containing selected courses, total credits, import results, and whether SQL was used
     */
    @PostMapping("/courses/select")
    public Map<String, Object> selectCourses(@RequestBody Map<String, Object> body) {
        String csvPath = Objects.toString(body.get("coursePlanCsvPath"), "");
        int maxCredits = body.containsKey("maxCredits")
            ? Integer.parseInt(String.valueOf(body.get("maxCredits")))
            : 18;
        if (maxCredits <= 0) maxCredits = 18;

        @SuppressWarnings("unchecked")
        List<String> completedList = (List<String>) body.getOrDefault("completedCourses", List.of());
        Set<String> completedCourses = new HashSet<>(completedList);

        Map<String, Object> response = new HashMap<>();
        List<?> selected = List.of();
        int totalCredits = 0;
        List<String> importErrors = new ArrayList<>();
        int importSuccess = 0;

        boolean usedSql = false;

        try {
            CsvImportService.ImportResult importResult = csvImportService.importCoursePlan(csvPath);
            importSuccess = importResult.getSuccessCount();
            importErrors.addAll(importResult.getErrors());

            List<CourseSelectionService.CourseInfo> sqlSelected =
                courseSelectionService.selectSixCourses(maxCredits, completedCourses);
            selected = sqlSelected;
            totalCredits = sqlSelected.stream()
                .mapToInt(CourseSelectionService.CourseInfo::getCreditHours)
                .sum();
            usedSql = true;

        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException dbEx) {
            System.err.println("SQL unavailable, falling back: " + dbEx.getMessage());
            try {
                var fallback = simpleCsvSelectionService.selectFromCsv(Path.of(csvPath), maxCredits, completedCourses);
                selected = fallback;
                totalCredits = fallback.stream()
                    .mapToInt(SimpleCsvSelectionService.CourseInfo::getCreditHours)
                    .sum();
                importErrors.add("SQL unavailable; used CSV-only fallback selection.");
            } catch (Exception parseEx) {
                importErrors.add("Fallback failed: " + parseEx.getMessage());
            }

        } catch (org.springframework.dao.DataAccessException dbEx) {
            System.err.println("SQL data access error, falling back: " + dbEx.getMessage());
            try {
                var fallback = simpleCsvSelectionService.selectFromCsv(Path.of(csvPath), maxCredits, completedCourses);
                selected = fallback;
                totalCredits = fallback.stream()
                    .mapToInt(SimpleCsvSelectionService.CourseInfo::getCreditHours)
                    .sum();
                importErrors.add("SQL error; used CSV-only fallback selection.");
            } catch (Exception parseEx) {
                importErrors.add("Fallback failed: " + parseEx.getMessage());
            }

        } catch (Exception ex) {
            importErrors.add("Unexpected error: " + ex.getMessage());
        } finally {
            response.put("courses", selected);
            response.put("totalCredits", totalCredits);
            response.put("importSuccess", importSuccess);
            response.put("importErrors", importErrors);
            response.put("usedSql", usedSql);
            return response;
        }
    }
}
