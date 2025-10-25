package com.courseguide.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadDir;

    public FileStorageService() throws IOException {
        // Create a per-run temp directory
        this.uploadDir = Files.createTempDirectory("courseguide-uploads-");
    }

    public String store(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        String ext = ".pdf"; // only PDFs supported for now
        Path dest = uploadDir.resolve(id + ext);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return id;
    }

    public Path resolve(String id) {
        return uploadDir.resolve(id + ".pdf");
    }
}