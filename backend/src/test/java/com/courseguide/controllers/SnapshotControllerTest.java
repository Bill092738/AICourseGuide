package com.courseguide.controllers;

import com.courseguide.services.DuckDuckGoSearchService;
import com.courseguide.services.WebPagePdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SnapshotController.class, HealthController.class})
class SnapshotControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SnapshotController controller;

    @MockBean
    WebPagePdfService pdfService;
    @MockBean
    DuckDuckGoSearchService search;

    @TempDir
    Path tmp;

    @Test
    void rendersValidUrl() throws Exception {
        when(pdfService.renderExpandedPageToPdf("https://osu.edu")).thenReturn(new byte[]{'%', 'P'});

        mvc.perform(get("/api/snapshot/pdf").param("url", "https://osu.edu"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"snapshot.pdf\""))
                .andExpect(content().bytes(new byte[]{'%', 'P'}));
    }

    @Test
    void rejectsInvalidUrl() throws Exception {
        mvc.perform(get("/api/snapshot/pdf").param("url", "file:///etc/passwd")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/snapshot/pdf").param("url", "not a url")).andExpect(status().isBadRequest());

        verify(pdfService, never()).renderExpandedPageToPdf(anyString());
    }

    @Test
    void searchRendersTopResult() throws Exception {
        when(search.resolveSearchUrl("osu cse")).thenReturn(Optional.of("https://osu.edu/cse"));
        when(pdfService.renderExpandedPageToPdf("https://osu.edu/cse")).thenReturn(new byte[]{1});

        mvc.perform(get("/api/snapshot/pdf/search").param("query", "osu cse")).andExpect(status().isOk());
    }

    @Test
    void searchErrors() throws Exception {
        when(search.resolveSearchUrl("nothing")).thenReturn(Optional.empty());
        when(search.resolveSearchUrl("ftp")).thenReturn(Optional.of("ftp://example.com"));

        mvc.perform(get("/api/snapshot/pdf/search").param("query", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/snapshot/pdf/search").param("query", "nothing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/snapshot/pdf/search").param("query", "ftp")).andExpect(status().isBadRequest());
    }

    @Test
    void findsMostRecentPdf() throws Exception {
        Path older = Files.writeString(tmp.resolve("a.pdf"), "a");
        Path newer = Files.writeString(tmp.resolve("b.pdf"), "b");
        Files.writeString(tmp.resolve("c.txt"), "c");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));

        assertThat(controller.findMostRecentPdf(tmp)).isEqualTo(newer);
        assertThat(controller.findMostRecentPdf(tmp.resolve("missing"))).isNull();
    }

    @Test
    void legacyHealthz() throws Exception {
        mvc.perform(get("/api/healthz")).andExpect(status().isOk()).andExpect(content().string("OK"));
    }
}
