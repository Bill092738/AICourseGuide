package com.courseguide.controllers;

import com.courseguide.dto.LlmConfig;
import com.courseguide.services.DuckDuckGoSearchService;
import com.courseguide.services.FileStorageService;
import com.courseguide.services.LlamaAnalysisService;
import com.courseguide.services.WebPagePdfService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FileStorageService storage;
    @MockBean
    WebPagePdfService pdfService;
    @MockBean
    LlamaAnalysisService llama;
    @MockBean
    DuckDuckGoSearchService search;

    private static final String BODY = """
            {"university":"The Ohio State University","major":"Computer Science","graduationYear":2027,
             "progressFileId":"abc","llmConfig":{"apiBaseUrl":"https://llm.example","apiKey":"k","modelName":"m"}}
            """;

    @Test
    void health() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void buildsSearchQueryAndSkipsAnalysisWhenNoUrlIsFound() throws Exception {
        when(search.resolveSearchUrl(anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursePlanAvailable").value(false))
                .andExpect(jsonPath("$.coursePlanXmlPath").value(""))
                .andExpect(jsonPath("$.summarySentence")
                        .value("The 2027 year graduate requirement of Computer Science at The Ohio State University"));

        verify(search).resolveSearchUrl("The Ohio State University Computer Science degree requirements 2027");
        verify(pdfService, never()).renderExpandedPageToPdf(anyString());
        verify(llama, never()).analyzeAndGenerateCoursePlan(any(), any(), any(), any());
    }

    @Test
    void runsAnalysisWithUploadedProgressAndUserLlmConfig() throws Exception {
        Path progress = Path.of("/uploads/abc.pdf");
        when(search.resolveSearchUrl(anyString())).thenReturn(Optional.of("https://osu.edu/cse"));
        when(pdfService.renderExpandedPageToPdf("https://osu.edu/cse")).thenReturn(new byte[]{1, 2, 3});
        when(storage.resolve("abc")).thenReturn(progress);
        when(llama.analyzeAndGenerateCoursePlan(any(), any(), eq(progress), any())).thenReturn("/plans/p.xml");

        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursePlanAvailable").value(true))
                .andExpect(jsonPath("$.coursePlanXmlPath").value("/plans/p.xml"));

        ArgumentCaptor<LlmConfig> config = ArgumentCaptor.forClass(LlmConfig.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> info = ArgumentCaptor.forClass(Map.class);
        verify(llama).analyzeAndGenerateCoursePlan(info.capture(), any(), eq(progress), config.capture());
        assertThat(config.getValue()).isEqualTo(new LlmConfig("https://llm.example", "k", "m"));
        assertThat(info.getValue()).containsEntry("major", "Computer Science").containsEntry("graduationYear", "2027");
    }

    @Test
    void pdfFailureStillReturnsAResponse() throws Exception {
        when(search.resolveSearchUrl(anyString())).thenReturn(Optional.of("https://osu.edu/cse"));
        when(pdfService.renderExpandedPageToPdf(anyString())).thenThrow(new RuntimeException("browser crashed"));

        mvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursePlanAvailable").value(false));

        verify(llama, never()).analyzeAndGenerateCoursePlan(any(), any(), any(), any());
    }

    @Test
    void profileEndpointPrefersTargetMajorAndReturnsRecommendations() throws Exception {
        when(search.resolveSearchUrl(anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/api/recommendations/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"user":{"university":"OSU","major":"History","graduationYear":2026},
                         "target":{"major":"Computer Science"}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summarySentence").value("The 2026 year graduate requirement of Computer Science at OSU"))
                .andExpect(jsonPath("$.recommendations[0]").exists());

        verify(search).resolveSearchUrl("OSU Computer Science degree requirements 2026");
    }

    @Test
    void sentenceGenerationUsesUnknownForMissingFields() {
        assertThat(RecommendationController.sentenceGeneration(Map.of("major", "CS")))
                .isEqualTo("The Unknown year graduate requirement of CS at Unknown");
    }
}
