package com.courseguide.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FileStorageService, PdfTextExtractorService, and WebPagePdfService input validation. */
class FileAndPdfServicesTest {

    @TempDir
    Path tmp;

    @Test
    void storesUploadUnderGeneratedIdAndResolvesIt() throws Exception {
        FileStorageService storage = new FileStorageService();
        byte[] bytes = "%PDF-1.4 test".getBytes();

        String id = storage.store(new MockMultipartFile("progressPdf", "me.pdf", "application/pdf", bytes));

        assertThat(id).matches("[0-9a-f-]{36}");
        Path stored = storage.resolve(id);
        assertThat(stored.getParent()).isEqualTo(storage.getUploadDir());
        assertThat(stored.getFileName().toString()).isEqualTo(id + ".pdf");
        assertThat(Files.readAllBytes(stored)).isEqualTo(bytes);
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        Path pdf = tmp.resolve("hello.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("CSE 2221 Software I");
                cs.endText();
            }
            doc.save(pdf.toFile());
        }

        assertThat(new PdfTextExtractorService().extractText(pdf)).contains("CSE 2221 Software I");
    }

    @Test
    void extractTextFailsForNonPdf() throws Exception {
        Path notPdf = Files.writeString(tmp.resolve("x.pdf"), "not a pdf");

        assertThatThrownBy(() -> new PdfTextExtractorService().extractText(notPdf)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void renderRejectsInvalidUrlsBeforeLaunchingABrowser() throws Exception {
        WebPagePdfService service = new WebPagePdfService();

        assertThatThrownBy(() -> service.renderExpandedPageToPdf(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.renderExpandedPageToPdf(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.renderExpandedPageToPdf("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }
}
