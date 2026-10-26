package com.courseguide.services;

import com.courseguide.support.CoursePlans;
import com.courseguide.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.courseguide.support.CoursePlans.course;
import static com.courseguide.support.CoursePlans.plan;
import static org.assertj.core.api.Assertions.assertThat;

class XmlImportServiceTest {

    @TempDir
    Path tmp;

    private JdbcTemplate jdbc;
    private XmlImportService service;

    @BeforeEach
    void setUp() {
        jdbc = TestDatabase.newJdbcTemplate();
        service = new XmlImportService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    @Test
    void reportsErrorWhenPathIsMissing() {
        XmlImportService.ImportResult result = service.importCoursePlan(tmp.resolve("missing.xml").toString());

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0)).contains("not found");
    }

    @Test
    void reportsErrorWhenPathIsBlank() {
        assertThat(service.importCoursePlan("  ").getErrors()).hasSize(1);
        assertThat(service.importCoursePlan(null).getErrors()).hasSize(1);
    }

    @Test
    void importsCoursesAndPrerequisiteEdges() throws Exception {
        Path xml = CoursePlans.write(tmp, CoursePlans.sample());

        XmlImportService.ImportResult result = service.importCoursePlan(xml.toString());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSuccessCount()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses", Integer.class)).isEqualTo(5);

        List<Map<String, Object>> edges = jdbc.queryForList("""
                SELECT dc.course_name AS c, pc.course_name AS p
                FROM prerequisites e
                JOIN courses dc ON e.dependent_course_id = dc.id
                JOIN courses pc ON e.prerequisite_course_id = pc.id
                ORDER BY c, p
                """);
        assertThat(edges).extracting(m -> m.get("c") + " <- " + m.get("p")).containsExactly(
                "CSE 2100 <- CSE 1100",
                "CSE 2200 <- CSE 1100",
                "CSE 2200 <- MATH 1150",
                "CSE 3100 <- CSE 2100");
    }

    @Test
    void normalizesCategoriesAndDefaultsCredits() throws Exception {
        Path xml = CoursePlans.write(tmp, plan(
                course("A 1", "", "0", "m2", ""),
                course("B 1", "", "4", "General Education", ""),
                course("C 1", "", "3", "minor in math", ""),
                course("D 1", "", "3", "cis", "")));

        service.importCoursePlan(xml.toString());

        Map<String, Object> a = jdbc.queryForMap("SELECT credit_hours, category FROM courses WHERE course_name = 'A 1'");
        assertThat(a.get("credit_hours")).isEqualTo(3);
        assertThat(a.get("category")).isEqualTo("Major2");
        assertThat(jdbc.queryForObject("SELECT category FROM courses WHERE course_name = 'B 1'", String.class)).isEqualTo("GenedEdu");
        assertThat(jdbc.queryForObject("SELECT category FROM courses WHERE course_name = 'C 1'", String.class)).isEqualTo("Minor");
        assertThat(jdbc.queryForObject("SELECT category FROM courses WHERE course_name = 'D 1'", String.class)).isEqualTo("Major1");
    }

    @Test
    void createsPlaceholderRowsForUnknownPrerequisites() throws Exception {
        Path xml = CoursePlans.write(tmp, plan(course("CSE 5000", "CSE 4000", "3", "Major1", "Capstone")));

        service.importCoursePlan(xml.toString());

        assertThat(jdbc.queryForObject(
                "SELECT description FROM courses WHERE course_name = 'CSE 4000'", String.class))
                .isEqualTo("Prerequisite course");
    }

    /*
     * Known bug: when a course appears as a prerequisite, importCoursePlan upserts a placeholder
     * row (3 credits, Major1, "Prerequisite course"). ON DUPLICATE KEY UPDATE then overwrites the
     * credits and category of a course that was already imported. Enable this test once fixed.
     */
    @Disabled("Known bug: prerequisite placeholder upsert overwrites credits/category of existing courses")
    @Test
    void prerequisiteReferencesDoNotOverwriteTheRealCourse() throws Exception {
        // MATH 1150 is listed both before and after courses that require it.
        Path xml = CoursePlans.write(tmp, plan(
                course("MATH 1150", "", "5", "GenedEdu", "Calculus I"),
                course("CSE 2200", "MATH 1150; CSE 2100", "3", "Major1", "Discrete math"),
                course("CSE 2100", "", "4", "Major2", "Data structures")));

        service.importCoursePlan(xml.toString());

        Map<String, Object> math = jdbc.queryForMap("SELECT credit_hours, category, description FROM courses WHERE course_name = 'MATH 1150'");
        assertThat(math).containsEntry("credit_hours", 5).containsEntry("category", "GenedEdu").containsEntry("description", "Calculus I");
        Map<String, Object> ds = jdbc.queryForMap("SELECT credit_hours, category, description FROM courses WHERE course_name = 'CSE 2100'");
        assertThat(ds).containsEntry("credit_hours", 4).containsEntry("category", "Major2").containsEntry("description", "Data structures");
    }

    @Test
    void reimportReplacesPreviousData() throws Exception {
        service.importCoursePlan(CoursePlans.write(tmp, CoursePlans.sample()).toString());
        service.importCoursePlan(CoursePlans.write(tmp, plan(course("ONLY 1", "", "3", "Major1", ""))).toString());

        assertThat(jdbc.queryForList("SELECT course_name FROM courses", String.class)).containsExactly("ONLY 1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prerequisites", Integer.class)).isZero();
    }
}
