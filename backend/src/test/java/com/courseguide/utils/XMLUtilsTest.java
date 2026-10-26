package com.courseguide.utils;

import com.courseguide.models.Course;
import com.courseguide.support.CoursePlans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.courseguide.support.CoursePlans.course;
import static com.courseguide.support.CoursePlans.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XMLUtilsTest {

    @TempDir
    Path tmp;

    @Test
    void parsesAllFieldsFromFile() throws Exception {
        Path xml = CoursePlans.write(tmp, plan(course(" CSE 2100 ", "CSE 1100;MATH 1150", " 4 ", "Major2", " Data structures ")));

        List<Course> courses = XMLUtils.parseCoursesFromXML(xml.toString());

        assertThat(courses).hasSize(1);
        Course c = courses.get(0);
        assertThat(c.getCourseName()).isEqualTo("CSE 2100");
        assertThat(c.getPreqCourseName()).isEqualTo("CSE 1100;MATH 1150");
        assertThat(c.getCreditHours()).isEqualTo(4);
        assertThat(c.getCategory()).isEqualTo("Major2");
        assertThat(c.getDescription()).isEqualTo("Data structures");
    }

    @Test
    void appliesDefaultsAndSkipsCoursesWithoutAName() {
        List<Course> courses = XMLUtils.parseCoursesFromXMLContent(plan(
                course("ART 1000", null, "three", null, null),
                course(null, "", "3", "Major1", "nameless"),
                course("   ", "", "3", "Major1", "blank")));

        assertThat(courses).hasSize(1);
        Course c = courses.get(0);
        assertThat(c.getCreditHours()).isEqualTo(3);
        assertThat(c.getCategory()).isEqualTo("Major1");
        assertThat(c.getDescription()).isEmpty();
        assertThat(c.getPreqCourseName()).isEmpty();
    }

    @Test
    void missingFileThrowsIOException() {
        assertThatThrownBy(() -> XMLUtils.parseCoursesFromXML(tmp.resolve("nope.xml").toString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void malformedContentReturnsEmptyList() {
        assertThat(XMLUtils.parseCoursesFromXMLContent("<coursePlan><course>")).isEmpty();
    }

    @Test
    void generatedXmlRoundTrips() {
        List<Course> original = List.of(
                new Course("CSE 1100", "", 3, "Major1", "Intro & basics <1>"),
                new Course("CSE 2100", "CSE 1100", 4, "Major2", null));

        String xml = XMLUtils.generateXML(original);
        List<Course> parsed = XMLUtils.parseCoursesFromXMLContent(xml);

        assertThat(xml).contains("<coursePlan>").contains("&amp;");
        assertThat(parsed).extracting(Course::getCourseName).containsExactly("CSE 1100", "CSE 2100");
        assertThat(parsed.get(0).getDescription()).isEqualTo("Intro & basics <1>");
        assertThat(parsed.get(1).getPreqCourseName()).isEqualTo("CSE 1100");
        assertThat(parsed.get(1).getCreditHours()).isEqualTo(4);
    }
}
