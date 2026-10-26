package com.courseguide.controllers;

import com.courseguide.services.WebPagePdfService;
import com.courseguide.services.DuckDuckGoSearchService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;

/**
 * Controller for snapshot PDF generation endpoints.
 * Handles direct URL-to-PDF conversion and search-based PDF generation.
 */
@RestController
@RequestMapping("/api")
public class SnapshotController {

    @Autowired
    private WebPagePdfService pdfService;

    @Autowired
    private DuckDuckGoSearchService searchService;

    /**
     * Generates a PDF snapshot from a given URL.
     *
     * @param url the URL to capture as PDF
     * @return the PDF as a byte array
     */
    @GetMapping(value = "/snapshot/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdf(@RequestParam String url) {
        if (url == null || url.isBlank() || !isValidHttpUrl(url)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valid http(s) url is required");
        }
        debugPrintSnapshotUrl("direct", url);
        byte[] pdf = pdfService.renderExpandedPageToPdf(url);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"snapshot.pdf\"")
            .body(pdf);
    }

    /**
     * Generates a PDF snapshot from the first DuckDuckGo search result.
     *
     * @param query the search query
     * @return the PDF as a byte array
     */
    @GetMapping(value = "/snapshot/pdf/search", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> snapshotPdfFromSearch(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        String resolvedUrl = searchService.resolveSearchUrl(query)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No DuckDuckGo result URL found"));
        if (!isValidHttpUrl(resolvedUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolved URL is not http(s)");
        }
        debugPrintSnapshotUrl("duckduckgo", resolvedUrl);
        byte[] pdf = pdfService.renderExpandedPageToPdf(resolvedUrl);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"snapshot.pdf\"")
            .body(pdf);
    }

    /**
     * Finds the most recently modified PDF file in a directory.
     *
     * @param directory the directory to search
     * @return the Path to the most recent PDF, or null if none found
     * @throws IOException if an I/O error occurs
     */
    public Path findMostRecentPdf(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return null;
        }
        
        return Files.list(directory)
            .filter(p -> p.toString().endsWith(".pdf"))
            .max(Comparator.comparing(p -> {
                try {
                    return Files.getLastModifiedTime(p);
                } catch (IOException e) {
                    return null;
                }
            }))
            .orElse(null);
    }

    /**
     * Debug printer for snapshot URLs.
     *
     * @param source the source of the URL (e.g., "direct", "duckduckgo")
     * @param url the URL to print
     */
    private static void debugPrintSnapshotUrl(String source, String url) {
        System.out.println("---- Debug: Snapshot URL (" + source + ") ----");
        System.out.println(url == null || url.isBlank() ? "(empty)" : url);
        System.out.println("---- End Snapshot URL ----");
    }

    /**
     * Validates if a URL is a valid HTTP or HTTPS URL.
     *
     * @param url the URL to validate
     * @return true if valid HTTP/HTTPS URL, false otherwise
     */
    private static boolean isValidHttpUrl(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
