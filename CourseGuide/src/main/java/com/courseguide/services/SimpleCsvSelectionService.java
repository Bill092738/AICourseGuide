package com.courseguide.services;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimpleCsvSelectionService {

    public static class CourseInfo {
        private String courseName;
        private int creditHours;
        private String category;
        private String description;
        private List<String> prerequisites;

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

    public List<CourseInfo> selectFromCsv(Path csvPath, int maxCredits, Set<String> completedCourses) throws Exception {
        if (maxCredits <= 0) maxCredits = 18;
        List<String> lines = Files.readAllLines(csvPath);

        Map<String, Integer> credits = new HashMap<>();
        Map<String, String> category = new HashMap<>();
        Map<String, String> description = new HashMap<>();
        Map<String, Set<String>> prereqs = new HashMap<>();

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            ParsedRow row = parseTolerant(line);
            if (row.courseName.isEmpty()) continue;

            credits.put(row.courseName, Math.max(1, row.creditHours));
            category.put(row.courseName, row.category);
            description.put(row.courseName, row.description);
            if (!row.prerequisites.isEmpty()) {
                prereqs.computeIfAbsent(row.courseName, k -> new HashSet<>()).addAll(row.prerequisites);
            }
        }

        // Selection
        Set<String> completed = completedCourses == null ? Set.of()
                : completedCourses.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet());

        Set<String> candidates = new HashSet<>(credits.keySet());
        candidates.removeAll(completed);

        List<CourseInfo> chosen = new ArrayList<>();
        int total = 0;
        Set<String> satisfied = new HashSet<>(completed);

        for (int pass = 0; pass < 50 && chosen.size() < 6; pass++) {
            boolean progress = false;
            List<String> ordered = candidates.stream()
                    .sorted(Comparator.comparing(this::sortKey))
                    .toList();

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

    // Robust row parser for LLM CSV:
    // name, [prereq1, prereq2, ...], credits, category, description...
    private ParsedRow parseTolerant(String line) {
        String[] t = line.split(",");
        List<String> tokens = new ArrayList<>();
        for (String s : t) tokens.add(s.trim());
        while (!tokens.isEmpty() && tokens.get(tokens.size()-1).isEmpty()) tokens.remove(tokens.size()-1);

        String name = tokens.isEmpty() ? "" : tokens.get(0);
        if (name.isEmpty()) return new ParsedRow();

        int i = 1;
        List<String> prereqTokens = new ArrayList<>();

        // Collect prereqs until we find a numeric credits token
        while (i < tokens.size() && !isInt(tokens.get(i))) {
            String tok = tokens.get(i);
            // Ignore empty; treat course-like tokens as possible prereqs
            if (!tok.isEmpty()) prereqTokens.add(tok);
            i++;
        }

        int creditHours = 3;
        if (i < tokens.size() && isInt(tokens.get(i))) {
            creditHours = Integer.parseInt(tokens.get(i));
            i++;
        }

        String category = "Major1";
        if (i < tokens.size() && !tokens.get(i).isEmpty()) {
            category = normalizeCategory(tokens.get(i));
            i++;
        }

        String desc = "";
        if (i < tokens.size()) {
            desc = String.join(",", tokens.subList(i, tokens.size())).trim();
        }

        // Split prereqs that may contain separators like ';' or '/'
        List<String> prereqs = prereqTokens.stream()
                .flatMap(s -> Arrays.stream(s.split("[;/]")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        ParsedRow row = new ParsedRow();
        row.courseName = name;
        row.creditHours = creditHours;
        row.category = category;
        row.description = desc;
        row.prerequisites = prereqs;
        return row;
    }

    private boolean isInt(String s) { return s != null && s.matches("^\\d+$"); }

    private String normalizeCategory(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return "Major1";
        if (s.equals("cis") || s.equals("m1") || s.equals("major1")) return "Major1";
        if (s.equals("m2") || s.equals("major2")) return "Major2";
        if (s.startsWith("gen")) return "GenedEdu";
        if (s.startsWith("minor")) return "Minor";
        return raw;
    }

    private String sortKey(String course) {
        String digits = course.replaceAll("\\D", "");
        String prefix = course.replaceAll("\\d", "").trim().toLowerCase(Locale.ROOT);
        String padded = digits.isEmpty() ? "00000" : String.format("%05d", Integer.parseInt(digits));
        return prefix + "-" + padded;
    }

    private static class ParsedRow {
        String courseName = "";
        int creditHours = 3;
        String category = "Major1";
        String description = "";
        List<String> prerequisites = List.of();
    }
}