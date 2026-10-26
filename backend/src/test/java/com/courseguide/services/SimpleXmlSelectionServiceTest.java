package com.courseguide.services;

import com.courseguide.services.SimpleXmlSelectionService.CourseInfo;
import com.courseguide.support.CoursePlans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.courseguide.support.CoursePlans.course;
import static com.courseguide.support.CoursePlans.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleXmlSelectionServiceTest {

    @TempDir
    Path tmp;

    private final SimpleXmlSelectionService service = new SimpleXmlSelectionService();

    private List<CourseInfo> select(String xml, int maxCredits, Set<String> completed) throws Exception {
        return service.selectFromXml(CoursePlans.write(tmp, xml), maxCredits, completed);
    }

    private static List<String> names(List<CourseInfo> courses) {
        return courses.stream().map(CourseInfo::getCourseName).toList();
    }

    private static int credits(List<CourseInfo> courses) {
        return courses.stream().mapToInt(CourseInfo::getCreditHours).sum();
    }

    @Test
    void selectsUnlockedCoursesInSubjectNumberOrder() throws Exception {
        List<CourseInfo> chosen = select(CoursePlans.sample(), 18, Set.of());

        assertThat(names(chosen)).containsExactly("CSE 1100", "CSE 2100", "CSE 3100", "MATH 1150", "CSE 2200");
        assertThat(credits(chosen)).isEqualTo(18);
    }

    @Test
    void onlyPicksCoursesWhosePrerequisitesAreSatisfied() throws Exception {
        List<CourseInfo> chosen = select(plan(
                course("CSE 3100", "CSE 2100", "3", "Major1", ""),
                course("CSE 2100", "CSE 9999", "3", "Major1", "")), 18, Set.of());

        assertThat(chosen).isEmpty();
    }

    @Test
    void respectsTheCreditLimit() throws Exception {
        List<CourseInfo> chosen = select(CoursePlans.sample(), 8, Set.of());

        assertThat(credits(chosen)).isLessThanOrEqualTo(8);
        assertThat(names(chosen)).containsExactly("CSE 1100", "CSE 2100");
    }

    @Test
    void completedCoursesAreExcludedButUnlockDependents() throws Exception {
        List<CourseInfo> chosen = select(CoursePlans.sample(), 18, Set.of("CSE 1100 ", "MATH 1150"));

        assertThat(names(chosen)).doesNotContain("CSE 1100", "MATH 1150").contains("CSE 2100", "CSE 2200");
    }

    @Test
    void capsSelectionAtSixCourses() throws Exception {
        String[] courses = new String[9];
        for (int i = 0; i < courses.length; i++) courses[i] = course("GEN " + (1000 + i), "", "2", "GenedEdu", "");

        assertThat(select(plan(courses), 30, Set.of())).hasSize(6);
    }

    @Test
    void appliesDefaultsForMissingOrInvalidFields() throws Exception {
        List<CourseInfo> chosen = select(plan(
                course("ART 1000", null, "abc", null, null),
                course("ART 1001", "", "0", "", ""),
                course("", "", "3", "Major1", "no name")), 18, null);

        assertThat(names(chosen)).containsExactly("ART 1000", "ART 1001");
        CourseInfo first = chosen.get(0);
        assertThat(first.getCreditHours()).isEqualTo(3);
        assertThat(first.getCategory()).isEqualTo("Major1");
        assertThat(first.getDescription()).isEmpty();
        assertThat(first.getPrerequisites()).isEmpty();
        assertThat(chosen.get(1).getCreditHours()).as("credits are at least 1").isEqualTo(1);
    }

    @Test
    void splitsPrerequisitesOnSemicolonSlashAndComma() throws Exception {
        List<CourseInfo> chosen = select(plan(
                course("A 1", "", "1", "Major1", ""),
                course("B 1", "", "1", "Major1", ""),
                course("C 1", "", "1", "Major1", ""),
                course("D 1", "A 1; B 1/C 1", "1", "Major1", "")), 18, Set.of());

        CourseInfo d = chosen.stream().filter(c -> c.getCourseName().equals("D 1")).findFirst().orElseThrow();
        assertThat(d.getPrerequisites()).containsExactlyInAnyOrder("A 1", "B 1", "C 1");
    }

    @Test
    void nonPositiveMaxCreditsDefaultsToEighteen() throws Exception {
        assertThat(credits(select(CoursePlans.sample(), 0, Set.of()))).isEqualTo(18);
    }

    @Test
    void throwsOnMalformedXml() throws Exception {
        Path bad = Files.writeString(tmp.resolve("bad.xml"), "<coursePlan><course>");

        assertThatThrownBy(() -> service.selectFromXml(bad, 18, Set.of()))
                .hasMessageContaining("Failed to parse XML file");
    }
}
