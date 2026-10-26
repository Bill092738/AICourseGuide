package com.courseguide.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds course plan XML files in the format produced by the LLM step. */
public final class CoursePlans {

    private CoursePlans() {}

    public static String course(String name, String prereqs, String credits, String category, String description) {
        StringBuilder sb = new StringBuilder("  <course>\n");
        if (name != null) sb.append("    <name>").append(name).append("</name>\n");
        if (prereqs != null) sb.append("    <prerequisites>").append(prereqs).append("</prerequisites>\n");
        if (credits != null) sb.append("    <creditHours>").append(credits).append("</creditHours>\n");
        if (category != null) sb.append("    <category>").append(category).append("</category>\n");
        if (description != null) sb.append("    <description>").append(description).append("</description>\n");
        return sb.append("  </course>\n").toString();
    }

    public static String plan(String... courses) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<coursePlan>\n" + String.join("", courses) + "</coursePlan>\n";
    }

    public static Path write(Path dir, String xml) throws IOException {
        Path file = Files.createTempFile(dir, "course_plan_", ".xml");
        Files.writeString(file, xml);
        return file;
    }

    /**
     * A small CSE plan:
     * CSE 1100 (3) -> CSE 2100 (4) -> CSE 3100 (3); MATH 1150 (5); CSE 2200 needs CSE 1100 and MATH 1150.
     */
    public static String sample() {
        return plan(
                course("CSE 1100", "", "3", "Major1", "Intro to programming"),
                course("CSE 2100", "CSE 1100", "4", "Major1", "Data structures"),
                course("CSE 3100", "CSE 2100", "3", "Major1", "Algorithms"),
                course("MATH 1150", "", "5", "GenedEdu", "Calculus I"),
                course("CSE 2200", "CSE 1100; MATH 1150", "3", "Major2", "Discrete math")
        );
    }
}
