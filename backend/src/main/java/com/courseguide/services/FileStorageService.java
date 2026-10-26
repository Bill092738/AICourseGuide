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
        System.out.println("---- DEBUG: File Upload Attempt ----");
        System.out.println("Original filename: " + file.getOriginalFilename());
        System.out.println("Size: " + file.getSize() + " bytes");
        System.out.println("Content type: " + file.getContentType());
        
        String id = UUID.randomUUID().toString();
        String ext = ".pdf"; // only PDFs supported for now
        Path dest = uploadDir.resolve(id + ext);
        
        System.out.println("Saving to: " + dest.toAbsolutePath());
        System.out.println("Directory exists: " + Files.exists(uploadDir));
        System.out.println("Directory writable: " + Files.isWritable(uploadDir));
        
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("File saved successfully: " + Files.exists(dest));
        System.out.println("Saved file size: " + Files.size(dest));
        System.out.println("---- END DEBUG ----");
        
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