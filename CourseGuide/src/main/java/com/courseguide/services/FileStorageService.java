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
        // Create uploads directory in the project workspace (not /tmp)
        Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
        this.uploadDir = workspaceRoot.resolve("uploads");
        
        // Create directory if it doesn't exist
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        System.out.println("---- Upload directory initialized ----");
        System.out.println("Location: " + uploadDir.toAbsolutePath());
        System.out.println("---- End ----");
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

    // Expose the upload directory for use by other services
    public Path getUploadDir() {
        return uploadDir;
    }
}