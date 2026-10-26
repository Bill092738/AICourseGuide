package com.courseguide.controllers;

import com.courseguide.services.UserProgressService;
import com.courseguide.services.UserProgressService.UserProgress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProgressController.class)
class ProgressControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    UserProgressService service;

    @Test
    void generatesCaseNumber() throws Exception {
        when(service.generateCaseNumber()).thenReturn("ABCD1234");

        mvc.perform(post("/api/progress/case-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber").value("ABCD1234"));
    }

    @Test
    void savesLocally() throws Exception {
        when(service.saveProgressLocal(any())).thenReturn("ABCD1234");

        mvc.perform(post("/api/progress/save/local").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formData\":\"{}\",\"totalCredits\":\"12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseNumber").value("ABCD1234"))
                .andExpect(jsonPath("$.mode").value("local"));

        verify(service).saveProgressLocal(argThat(p -> "12".equals(p.getTotalCredits())));
    }

    @Test
    void localSaveFailureReturns500() throws Exception {
        when(service.saveProgressLocal(any())).thenThrow(new RuntimeException("db down"));

        mvc.perform(post("/api/progress/save/local").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void onlineSaveReportsWhetherItWasSynced() throws Exception {
        UserProgress local = new UserProgress();
        local.setSyncedToCloud(false);
        when(service.saveProgressOnline(any())).thenReturn("ABCD1234");
        when(service.loadProgressLocal("ABCD1234")).thenReturn(local);

        mvc.perform(post("/api/progress/save/online").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("online"))
                .andExpect(jsonPath("$.synced").value(false));
    }

    @Test
    void loadUsesRequestedMode() throws Exception {
        UserProgress p = new UserProgress("ABCD1234", "{}", "", "[]", "9");
        when(service.loadProgressLocal("ABCD1234")).thenReturn(p);
        when(service.loadProgressOnline("ONLINE01")).thenReturn(p);

        mvc.perform(get("/api/progress/load/ABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value("9"));
        mvc.perform(get("/api/progress/load/ONLINE01").param("mode", "online"))
                .andExpect(status().isOk());

        verify(service).loadProgressOnline("ONLINE01");
    }

    @Test
    void loadReturns404WhenMissing() throws Exception {
        mvc.perform(get("/api/progress/load/NOPE0000")).andExpect(status().isNotFound());
    }

    @Test
    void listsProgress() throws Exception {
        when(service.listAllProgressLocal()).thenReturn(List.of(new UserProgress("A1", "", "", "", ""), new UserProgress("B2", "", "", "", "")));

        mvc.perform(get("/api/progress/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].caseNumber").value("B2"));
    }

    @Test
    void deleteReturns200Or404() throws Exception {
        when(service.deleteProgressLocal("ABCD1234")).thenReturn(true);

        mvc.perform(delete("/api/progress/delete/ABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
        mvc.perform(delete("/api/progress/delete/NOPE0000")).andExpect(status().isNotFound());
    }

    @Test
    void syncReportsRemainingRecords() throws Exception {
        when(service.getUnsyncedCount()).thenReturn(3);
        when(service.syncLocalToOnline()).thenReturn(2);

        mvc.perform(post("/api/progress/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedCount").value(2))
                .andExpect(jsonPath("$.remaining").value(1));
    }

    @Test
    void syncStatus() throws Exception {
        when(service.getUnsyncedCount()).thenReturn(0);

        mvc.perform(get("/api/progress/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsyncedCount").value(0))
                .andExpect(jsonPath("$.needsSync").value(false));
    }
}
