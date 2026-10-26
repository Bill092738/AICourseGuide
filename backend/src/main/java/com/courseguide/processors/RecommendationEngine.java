package com.courseguide.processors;

import java.util.ArrayList;
import java.util.List;

public class RecommendationEngine {
    public List<String> generateRecommendations(String major, double gpa) {
        List<String> recs = new ArrayList<>();
        String m = major == null ? "" : major.trim().toLowerCase();
        if (m.contains("cs") || m.contains("computer") || m.contains("software")) {
            if (gpa >= 3.7) {
                recs.add("Apply to SWE internships at top-tier companies");
                recs.add("Contribute to popular open-source projects");
            } else if (gpa >= 3.0) {
                recs.add("Build a full-stack portfolio project");
                recs.add("Practice data structures and algorithms 30 min/day");
            } else {
                recs.add("Focus on fundamentals: Java, data structures, and Git");
                recs.add("Complete 2 coding projects this semester");
            }
        } else if (m.contains("data")) {
            if (gpa >= 3.5) {
                recs.add("Participate in Kaggle competitions");
                recs.add("Prepare for SQL and statistics interviews");
            } else {
                recs.add("Complete a data analysis project with public datasets");
                recs.add("Learn basic Python data stack (pandas, matplotlib)");
            }
        } else {
            recs.add("Join a relevant campus club and network");
            recs.add("Create a one-page resume and LinkedIn profile");
        }
        recs.add("Schedule mock interviews monthly");
        return recs;
    }
}
