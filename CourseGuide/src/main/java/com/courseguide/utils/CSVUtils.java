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
            if (fields.length >= 5) {
                try {
                    String courseName = fields[0].trim();
                    String preqCourseName = fields[1].trim();
                    int creditHours = Integer.parseInt(fields[2].trim());
                    String category = fields[3].trim();
                    String description = fields[4].trim();
                    
                    Course course = new Course(courseName, preqCourseName, creditHours, category, description);
                    courses.add(course);
                } catch (NumberFormatException e) {
                    System.err.println("Skipping invalid row " + (i + 1) + ": " + line);
                }
            }
        }
        
        return courses;
    }
    
    /**
     * Parses courses from CSV content string
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
            if (fields.length >= 5) {
                try {
                    String courseName = fields[0].trim();
                    String preqCourseName = fields[1].trim();
                    int creditHours = Integer.parseInt(fields[2].trim());
                    String category = fields[3].trim();
                    String description = fields[4].trim();
                    
                    Course course = new Course(courseName, preqCourseName, creditHours, category, description);
                    courses.add(course);
                } catch (NumberFormatException e) {
                    System.err.println("Skipping invalid row " + (i + 1) + ": " + line);
                }
            }
        }
        
        return courses;
    }
}
