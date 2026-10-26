package com.courseguide.services;

import com.courseguide.support.CoursePlans;
import com.courseguide.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.courseguide.support.CoursePlans.course;
import static com.courseguide.support.CoursePlans.plan;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the SQL-backed selection on data loaded through XmlImportService. */
class CourseSelectionServiceTest {

    @TempDir
    Path tmp;

    private XmlImportService importer;
    private CourseSelectionService selector;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = TestDatabase.newJdbcTemplate();
        importer = new XmlImportService();
        selector = new CourseSelectionService();
        ReflectionTestUtils.setField(importer, "jdbc", jdbc);
        ReflectionTestUtils.setField(selector, "jdbc", jdbc);
    }

    private void load(String xml) throws Exception {
        importer.importCoursePlan(CoursePlans.write(tmp, xml).toString());
    }

    private static List<String> names(List<CourseSelectionService.CourseInfo> courses) {
        return courses.stream().map(CourseSelectionService.CourseInfo::getCourseName).toList();
    }

    @Test
    void selectsPrerequisiteChainsWithinTheCreditLimit() throws Exception {
        load(CoursePlans.sample());

        List<CourseSelectionService.CourseInfo> chosen = selector.selectSixCourses(18, Set.of());

        // Pass 1 walks courses in subject/number order and takes everything already unlocked;
        // CSE 2200 needs MATH 1150, which is only taken later in that pass, so it lands in pass 2.
        assertThat(names(chosen)).containsExactly("CSE 1100", "CSE 2100", "CSE 3100", "MATH 1150", "CSE 2200");
        // Credit totals are not asserted here: see XmlImportServiceTest#prerequisiteReferencesDoNotOverwriteTheRealCourse.
    }

    @Test
    void neverExceedsMaxCredits() throws Exception {
        load(CoursePlans.sample());

        List<CourseSelectionService.CourseInfo> chosen = selector.selectSixCourses(7, Set.of());

        assertThat(chosen.stream().mapToInt(CourseSelectionService.CourseInfo::getCreditHours).sum()).isLessThanOrEqualTo(7);
        assertThat(names(chosen)).containsExactly("CSE 1100", "CSE 2100");
    }

    @Test
    void skipsCompletedCoursesAndTreatsThemAsSatisfied() throws Exception {
        load(CoursePlans.sample());

        List<CourseSelectionService.CourseInfo> chosen = selector.selectSixCourses(18, Set.of(" CSE 1100 ", "CSE 2100"));

        assertThat(names(chosen)).doesNotContain("CSE 1100", "CSE 2100").contains("CSE 3100");
    }

    @Test
    void returnsAtMostSixCourses() throws Exception {
        String[] courses = new String[10];
        for (int i = 0; i < courses.length; i++) courses[i] = course("GEN " + (1000 + i), "", "1", "GenedEdu", "");
        load(plan(courses));

        assertThat(selector.selectSixCourses(30, Set.of())).hasSize(6);
    }

    @Test
    void nonPositiveMaxCreditsFallsBackToEighteen() throws Exception {
        load(CoursePlans.sample());

        assertThat(selector.selectSixCourses(0, null)).isNotEmpty();
    }

    @Test
    void reportsPrerequisitesOfSelectedCourses() throws Exception {
        load(CoursePlans.sample());

        CourseSelectionService.CourseInfo discrete = selector.selectSixCourses(18, Set.of()).stream()
                .filter(c -> c.getCourseName().equals("CSE 2200")).findFirst().orElseThrow();

        assertThat(discrete.getPrerequisites()).containsExactlyInAnyOrder("CSE 1100", "MATH 1150");
        assertThat(discrete.getCategory()).isEqualTo("Major2");
    }
}
