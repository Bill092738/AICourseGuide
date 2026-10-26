package com.courseguide.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseSelectionService {

    @Autowired
    private JdbcTemplate jdbc;

    public static class CourseInfo {
        private String courseName;
        private int creditHours;
        private String category;
        private String description;
        private List<String> prerequisites;

        public CourseInfo() {}

        public CourseInfo(String courseName, int creditHours, String category, String description, List<String> prerequisites) {
            this.courseName = courseName;
            this.creditHours = creditHours;
            this.category = category;
            this.description = description;
            this.prerequisites = prerequisites == null ? List.of() : prerequisites;
        }

        public String getCourseName() { return courseName; }
        public int getCreditHours() { return creditHours; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public List<String> getPrerequisites() { return prerequisites; }
    }

    public List<CourseInfo> selectSixCourses(int maxCredits, Set<String> completedCourses) {
        if (maxCredits <= 0) maxCredits = 18;

        Map<String, Integer> credits = new HashMap<>();
        Map<String, String> category = new HashMap<>();
        Map<String, String> description = new HashMap<>();
        Map<String, Set<String>> prereqs = new HashMap<>();

        jdbc.query("SELECT course_name, credit_hours, category, description FROM courses", rs -> {
            String name = rs.getString("course_name");
            credits.put(name, Math.max(1, rs.getInt("credit_hours")));
            category.put(name, rs.getString("category"));
            description.put(name, Optional.ofNullable(rs.getString("description")).orElse(""));
        });

        jdbc.query("""
            SELECT dc.course_name AS c, pc.course_name AS p
            FROM prerequisites p
            JOIN courses dc ON p.dependent_course_id = dc.id
            JOIN courses pc ON p.prerequisite_course_id = pc.id
        """, rs -> {
            String c = rs.getString("c");
            String p = rs.getString("p");
            prereqs.computeIfAbsent(c, k -> new HashSet<>()).add(p);
        });

        Set<String> candidates = new HashSet<>(credits.keySet());
        Set<String> completed = normalize(completedCourses);
        candidates.removeAll(completed);

        List<CourseInfo> chosen = new ArrayList<>();
        int total = 0;
        Set<String> satisfied = new HashSet<>(completed);

        for (int pass = 0; pass < 50 && chosen.size() < 6; pass++) {
            boolean progress = false;

            List<String> ordered = candidates.stream()
                .sorted(Comparator.comparing(this::keyForSort))
                .collect(Collectors.toList());

            for (String c : ordered) {
                if (chosen.size() >= 6) break;
                int cCredit = credits.getOrDefault(c, 3);
                if (total + cCredit > maxCredits) continue;

                Set<String> ps = prereqs.getOrDefault(c, Set.of());
                boolean ok = ps.stream().allMatch(satisfied::contains);
                if (!ok) continue;

                chosen.add(new CourseInfo(
                    c,
                    cCredit,
                    category.getOrDefault(c, "Major1"),
                    description.getOrDefault(c, ""),
                    new ArrayList<>(ps)
                ));
                total += cCredit;
                satisfied.add(c);
                candidates.remove(c);
                progress = true;
            }
            if (!progress) break;
        }

        return chosen;
    }

    private Set<String> normalize(Set<String> s) {
        if (s == null) return Set.of();
        return s.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet());
    }

    private String keyForSort(String course) {
        String digits = course.replaceAll("\\D", "");
        String prefix = course.replaceAll("\\d", "").trim().toLowerCase(Locale.ROOT);
        String padded = digits.isEmpty() ? "00000" : String.format("%05d", Integer.parseInt(digits));
        return prefix + "-" + padded;
    }
}