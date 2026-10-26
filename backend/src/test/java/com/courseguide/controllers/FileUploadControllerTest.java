package com.courseguide.controllers;

import com.courseguide.services.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileUploadController.class)
class FileUploadControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FileStorageService storage;

    @Test
    void storesPdfAndReturnsId() throws Exception {
        when(storage.store(any())).thenReturn("file-id");

        mvc.perform(multipart("/api/upload-progress")
                        .file(new MockMultipartFile("progressPdf", "progress.pdf", "application/pdf", "%PDF".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stored"))
                .andExpect(jsonPath("$.progressFileId").value("file-id"));
    }

    @Test
    void acceptsPdfExtensionWithGenericContentType() throws Exception {
        when(storage.store(any())).thenReturn("file-id");

        mvc.perform(multipart("/api/upload-progress")
                        .file(new MockMultipartFile("progressPdf", "PROGRESS.PDF", "application/octet-stream", "%PDF".getBytes())))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsNonPdf() throws Exception {
        mvc.perform(multipart("/api/upload-progress")
                        .file(new MockMultipartFile("progressPdf", "notes.txt", "text/plain", "hi".getBytes())))
                .andExpect(status().isBadRequest());

        verify(storage, never()).store(any());
    }

    @Test
    void rejectsEmptyOrMissingFile() throws Exception {
        mvc.perform(multipart("/api/upload-progress")
                        .file(new MockMultipartFile("progressPdf", "empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/upload-progress")).andExpect(status().isBadRequest());
    }

    @Test
    void storageFailureReturns500() throws Exception {
        when(storage.store(any())).thenThrow(new IOException("disk full"));

        mvc.perform(multipart("/api/upload-progress")
                        .file(new MockMultipartFile("progressPdf", "p.pdf", "application/pdf", "%PDF".getBytes())))
                .andExpect(status().isInternalServerError());
    }
}
