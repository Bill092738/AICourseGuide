package com.courseguide.utils;

import com.courseguide.models.Course;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CSVUtils {
    // Minimal CSV splitter: splits by comma, no quotes/escapes handling to keep it simple
    public static String[] split(String line) {
        return line.split(",");
    }
    
    /**
     * Parses courses from a CSV file with the format:
     * CourseName,PreqCourseName,CreditHours,Major1/Major2/GenedEdu/Minor,Description
     * Tolerant to missing/invalid fields; defaults:
     * - creditHours -> 3
     * - category    -> "Major1"
     * - description -> ""
     */
    public static List<Course> parseCoursesFromCSV(String filePath) throws IOException {
        List<Course> courses = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        
        // Skip header row if it exists
        int startIndex = 0;
        if (!lines.isEmpty() && lines.get(0).toLowerCase().contains("coursename")) {
            startIndex = 1;
        }
        
        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            
            String[] fields = split(line);
            // Pad missing fields
            String courseName      = fields.length > 0 ? fields[0].trim() : "";
            String preqCourseName  = fields.length > 1 ? fields[1].trim() : "";
            String creditStr       = fields.length > 2 ? fields[2].trim() : "";
            String category        = fields.length > 3 ? fields[3].trim() : "Major1";
            String description     = fields.length > 4 ? String.join(",", Arrays.copyOfRange(fields, 4, fields.length)).trim() : "";

            if (courseName.isEmpty()) {
                System.err.println("Skipping row " + (i + 1) + " due to empty course name: " + line);
                continue;
            }

            int creditHours = 3;
            if (!creditStr.isEmpty()) {
                try {
                    creditHours = Integer.parseInt(creditStr);
                } catch (NumberFormatException e) {
                    System.err.println("Row " + (i + 1) + " invalid credits '" + creditStr + "', defaulting to 3 for: " + courseName);
                }
            }

            if (category.isEmpty()) {
                category = "Major1";
            }

            Course course = new Course(courseName, preqCourseName, creditHours, category, description);
            courses.add(course);
        }
        
        return courses;
    }
    
    /**
     * Parses courses from CSV content string (tolerant).
     */
    public static List<Course> parseCoursesFromCSVContent(String csvContent) {
        List<Course> courses = new ArrayList<>();
        String[] lines = csvContent.split("\n");
        
        // Skip header row if it exists
        int startIndex = 0;
        if (lines.length > 0 && lines[0].toLowerCase().contains("coursename")) {
            startIndex = 1;
        }
        
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            String[] fields = split(line);
            String courseName      = fields.length > 0 ? fields[0].trim() : "";
            String preqCourseName  = fields.length > 1 ? fields[1].trim() : "";
            String creditStr       = fields.length > 2 ? fields[2].trim() : "";
            String category        = fields.length > 3 ? fields[3].trim() : "Major1";
            String description     = fields.length > 4 ? String.join(",", Arrays.copyOfRange(fields, 4, fields.length)).trim() : "";

            if (courseName.isEmpty()) {
                System.err.println("Skipping line " + (i + 1) + " due to empty course name: " + line);
                continue;
            }

            int creditHours = 3;
            if (!creditStr.isEmpty()) {
                try {
                    creditHours = Integer.parseInt(creditStr);
                } catch (NumberFormatException e) {
                    System.err.println("Line " + (i + 1) + " invalid credits '" + creditStr + "', defaulting to 3 for: " + courseName);
                }
            }

            if (category.isEmpty()) {
                category = "Major1";
            }

            Course course = new Course(courseName, preqCourseName, creditHours, category, description);
            courses.add(course);
        }
        
        return courses;
    }
}
