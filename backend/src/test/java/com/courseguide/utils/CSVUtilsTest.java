package com.courseguide.utils;

import com.courseguide.models.Course;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CSVUtilsTest {

    @TempDir
    Path tmp;

    private static final String CSV = """
            CourseName,PreqCourseName,CreditHours,Category,Description
            CSE 1100,,3,Major1,Intro to programming
            CSE 2100,CSE 1100,4,Major2,Data structures, with lab

            ,CSE 1100,3,Major1,missing name
            ART 1000
            ART 2000,,x,,
            """;

    @Test
    void splitsOnCommas() {
        assertThat(CSVUtils.split("a,b,,c")).containsExactly("a", "b", "", "c");
    }

    @Test
    void parsesContentSkippingHeaderBlankLinesAndNamelessRows() {
        List<Course> courses = CSVUtils.parseCoursesFromCSVContent(CSV);

        assertThat(courses).extracting(Course::getCourseName).containsExactly("CSE 1100", "CSE 2100", "ART 1000", "ART 2000");
        assertThat(courses.get(1).getPreqCourseName()).isEqualTo("CSE 1100");
        assertThat(courses.get(1).getDescription()).as("extra commas belong to the description").isEqualTo("Data structures, with lab");
    }

    @Test
    void appliesDefaultsForMissingFields() {
        List<Course> courses = CSVUtils.parseCoursesFromCSVContent(CSV);

        Course art1000 = courses.get(2);
        assertThat(art1000.getCreditHours()).isEqualTo(3);
        assertThat(art1000.getCategory()).isEqualTo("Major1");
        Course art2000 = courses.get(3);
        assertThat(art2000.getCreditHours()).as("invalid credits default to 3").isEqualTo(3);
        assertThat(art2000.getCategory()).isEqualTo("Major1");
    }

    @Test
    void fileAndContentParsingAgree() throws Exception {
        Path file = Files.writeString(tmp.resolve("plan.csv"), CSV);

        assertThat(CSVUtils.parseCoursesFromCSV(file.toString()))
                .extracting(Course::toString)
                .containsExactlyElementsOf(CSVUtils.parseCoursesFromCSVContent(CSV).stream().map(Course::toString).toList());
    }
}
