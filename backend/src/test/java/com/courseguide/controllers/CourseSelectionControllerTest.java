package com.courseguide.controllers;

import com.courseguide.services.CourseSelectionService;
import com.courseguide.services.SimpleXmlSelectionService;
import com.courseguide.services.XmlImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseSelectionController.class)
class CourseSelectionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    XmlImportService xmlImportService;
    @MockBean
    CourseSelectionService courseSelectionService;
    @MockBean
    SimpleXmlSelectionService simpleXmlSelectionService;

    private static final String BODY = """
            {"coursePlanXmlPath":"/plans/p.xml","maxCredits":12,"completedCourses":["CSE 1100"]}
            """;

    private XmlImportService.ImportResult okImport() {
        XmlImportService.ImportResult r = mock(XmlImportService.ImportResult.class);
        when(r.getSuccessCount()).thenReturn(4);
        when(r.getErrors()).thenReturn(List.of());
        return r;
    }

    @Test
    void usesSqlSelectionWhenDatabaseIsAvailable() throws Exception {
        XmlImportService.ImportResult imported = okImport();
        when(xmlImportService.importCoursePlan("/plans/p.xml")).thenReturn(imported);
        when(courseSelectionService.selectSixCourses(12, Set.of("CSE 1100"))).thenReturn(List.of(
                new CourseSelectionService.CourseInfo("CSE 2100", 4, "Major1", "DS", List.of("CSE 1100")),
                new CourseSelectionService.CourseInfo("MATH 1150", 5, "GenedEdu", "", List.of())));

        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedSql").value(true))
                .andExpect(jsonPath("$.importSuccess").value(4))
                .andExpect(jsonPath("$.totalCredits").value(9))
                .andExpect(jsonPath("$.courses[0].courseName").value("CSE 2100"))
                .andExpect(jsonPath("$.courses[0].prerequisites[0]").value("CSE 1100"));
    }

    @Test
    void fallsBackToXmlWhenDatabaseIsUnreachable() throws Exception {
        XmlImportService.ImportResult imported = okImport();
        when(xmlImportService.importCoursePlan("/plans/p.xml")).thenReturn(imported);
        when(courseSelectionService.selectSixCourses(anyInt(), anySet()))
                .thenThrow(new CannotGetJdbcConnectionException("refused"));
        when(simpleXmlSelectionService.selectFromXml(Path.of("/plans/p.xml"), 12, Set.of("CSE 1100"))).thenReturn(List.of(
                new SimpleXmlSelectionService.CourseInfo("CSE 2100", 4, "Major1", "DS", List.of())));

        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedSql").value(false))
                .andExpect(jsonPath("$.totalCredits").value(4))
                .andExpect(jsonPath("$.importErrors[0]").value("SQL unavailable; used XML-only fallback selection."));
    }

    @Test
    void fallsBackToXmlOnOtherSqlErrors() throws Exception {
        when(xmlImportService.importCoursePlan("/plans/p.xml"))
                .thenThrow(new BadSqlGrammarException("import", "SELECT", new SQLException("bad")));
        when(simpleXmlSelectionService.selectFromXml(eq(Path.of("/plans/p.xml")), anyInt(), anySet())).thenReturn(List.of());

        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importErrors[0]").value("SQL error; used XML-only fallback selection."));
    }

    @Test
    void reportsFallbackFailure() throws Exception {
        when(xmlImportService.importCoursePlan("/plans/p.xml")).thenThrow(new CannotGetJdbcConnectionException("refused"));
        when(simpleXmlSelectionService.selectFromXml(eq(Path.of("/plans/p.xml")), anyInt(), anySet()))
                .thenThrow(new Exception("Failed to parse XML file"));

        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0))
                .andExpect(jsonPath("$.importErrors[0]").value("Fallback failed: Failed to parse XML file"));
    }

    @Test
    void defaultsMaxCreditsToEighteen() throws Exception {
        XmlImportService.ImportResult imported = okImport();
        when(xmlImportService.importCoursePlan("/p.xml")).thenReturn(imported);

        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coursePlanXmlPath\":\"/p.xml\",\"maxCredits\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/courses/select").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coursePlanXmlPath\":\"/p.xml\"}"))
                .andExpect(status().isOk());

        verify(courseSelectionService, org.mockito.Mockito.times(2)).selectSixCourses(18, Set.of());
    }
}
