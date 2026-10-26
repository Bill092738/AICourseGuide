package com.courseguide.controllers;

import com.courseguide.services.FileStorageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

/**
 * Controller for file upload endpoints.
 * Handles progress PDF uploads and storage.
 */
@RestController
@RequestMapping("/api")
public class FileUploadController {

    @Autowired
    private FileStorageService storage;

    /**
     * Uploads a progress PDF file and stores it temporarily.
     * Returns an ID to reference the file later.
     *
     * @param progressPdf the PDF file to upload
     * @return a map containing the status and file ID
     */
    @PostMapping(value = "/upload-progress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadProgress(@RequestPart("progressPdf") MultipartFile progressPdf) {
        if (progressPdf == null || progressPdf.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing progressPdf");
        }
        String ctype = progressPdf.getContentType() == null ? "" : progressPdf.getContentType();
        String filename = progressPdf.getOriginalFilename() == null ? "" : progressPdf.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!ctype.contains("pdf") && !filename.endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF is allowed");
        }
        try {
            String id = storage.store(progressPdf);
            return Map.of("status", "stored", "progressFileId", id);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }
}
