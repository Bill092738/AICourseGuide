package com.courseguide.services;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Service for selecting courses from XML course plans.
 * Provides a fallback mechanism when SQL database is unavailable.
 */
@Service
public class SimpleXmlSelectionService {

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

    /**
     * Selects courses from an XML file based on credit limits and completed courses.
     *
     * @param xmlPath path to the XML file
     * @param maxCredits maximum credit hours allowed
     * @param completedCourses set of completed course names
     * @return list of selected CourseInfo objects
     * @throws Exception if parsing fails
     */
    public List<CourseInfo> selectFromXml(Path xmlPath, int maxCredits, Set<String> completedCourses) throws Exception {
        if (maxCredits <= 0) maxCredits = 18;

        Map<String, Integer> credits = new HashMap<>();
        Map<String, String> category = new HashMap<>();
        Map<String, String> description = new HashMap<>();
        Map<String, Set<String>> prereqs = new HashMap<>();

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlPath.toFile());
            doc.getDocumentElement().normalize();

            NodeList courseNodes = doc.getElementsByTagName("course");
            for (int i = 0; i < courseNodes.getLength(); i++) {
                Element courseElement = (Element) courseNodes.item(i);
                
                String courseName = getElementValue(courseElement, "name");
                if (courseName == null || courseName.isBlank()) continue;

                int creditHours = 3;
                String creditStr = getElementValue(courseElement, "creditHours");
                if (creditStr != null && !creditStr.isBlank()) {
                    try {
                        creditHours = Math.max(1, Integer.parseInt(creditStr.trim()));
                    } catch (NumberFormatException e) {
                        // Keep default
                    }
                }

                String cat = getElementValue(courseElement, "category");
                if (cat == null || cat.isBlank()) cat = "Major1";

                String desc = getElementValue(courseElement, "description");
                if (desc == null) desc = "";

                String prereqStr = getElementValue(courseElement, "prerequisites");
                Set<String> prereqSet = new HashSet<>();
                if (prereqStr != null && !prereqStr.isBlank()) {
                    Arrays.stream(prereqStr.split("[;/,]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(prereqSet::add);
                }

                credits.put(courseName, creditHours);
                category.put(courseName, cat);
                description.put(courseName, desc);
                if (!prereqSet.isEmpty()) {
                    prereqs.put(courseName, prereqSet);
                }
            }
        } catch (Exception e) {
            throw new Exception("Failed to parse XML file: " + e.getMessage(), e);
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

    /**
     * Gets the text content of a child element.
     *
     * @param parent the parent element
     * @param tagName the tag name of the child element
     * @return the text content, or null if element not found
     */
    private String getElementValue(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        Element element = (Element) nodeList.item(0);
        if (element == null) {
            return null;
        }
        Node node = element.getFirstChild();
        return node != null ? node.getNodeValue() : null;
    }

    private String sortKey(String course) {
        String digits = course.replaceAll("\\D", "");
        String prefix = course.replaceAll("\\d", "").trim().toLowerCase(Locale.ROOT);
        String padded = digits.isEmpty() ? "00000" : String.format("%05d", Integer.parseInt(digits));
        return prefix + "-" + padded;
    }
}
