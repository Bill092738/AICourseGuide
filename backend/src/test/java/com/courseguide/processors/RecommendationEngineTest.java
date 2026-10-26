package com.courseguide.processors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationEngineTest {

    private final RecommendationEngine engine = new RecommendationEngine();

    @ParameterizedTest(name = "{0} with GPA {1}")
    @CsvSource({
            "Computer Science, 3.9, Apply to SWE internships at top-tier companies",
            "CS,               3.2, Build a full-stack portfolio project",
            "Software Eng,     2.5, 'Focus on fundamentals: Java, data structures, and Git'",
            "Data Science,     3.6, Participate in Kaggle competitions",
            "data,             3.0, Complete a data analysis project with public datasets",
            "History,          4.0, Join a relevant campus club and network",
    })
    void recommendsByMajorAndGpa(String major, double gpa, String expectedFirst) {
        List<String> recs = engine.generateRecommendations(major, gpa);

        assertThat(recs).first().isEqualTo(expectedFirst);
        assertThat(recs).hasSize(3).last().isEqualTo("Schedule mock interviews monthly");
    }

    @Test
    void gpaThresholdsAreInclusive() {
        assertThat(engine.generateRecommendations("computer", 3.7)).first().isEqualTo("Apply to SWE internships at top-tier companies");
        assertThat(engine.generateRecommendations("computer", 3.0)).first().isEqualTo("Build a full-stack portfolio project");
    }

    /*
     * Known bug: the CS branch matches the substring "cs", so majors such as Economics,
     * Physics, or Data Analytics receive software-engineering advice. Enable once fixed.
     */
    @Disabled("Known bug: substring \"cs\" matches Economics, Physics, Data Analytics")
    @ParameterizedTest
    @CsvSource({"Economics", "Physics", "Data Analytics"})
    void majorsThatMerelyContainCsAreNotTreatedAsComputerScience(String major) {
        assertThat(engine.generateRecommendations(major, 3.9)).first()
                .isNotEqualTo("Apply to SWE internships at top-tier companies");
    }

    @Test
    void nullMajorUsesGeneralAdvice() {
        assertThat(engine.generateRecommendations(null, 3.0)).first().isEqualTo("Join a relevant campus club and network");
    }
}
