package com.courseguide.services;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class WebPagePdfService {

    @Autowired
    private FileStorageService fileStorageService;

    private Path snapshotDir;

    public WebPagePdfService() throws IOException {
        // Create snapshot directory in the project workspace (not /tmp)
        Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
        this.snapshotDir = workspaceRoot.resolve("snapshots");
        
        // Create directory if it doesn't exist
        if (!Files.exists(snapshotDir)) {
            Files.createDirectories(snapshotDir);
        }
        
        System.out.println("---- Snapshot directory initialized ----");
        System.out.println("Location: " + snapshotDir.toAbsolutePath());
        System.out.println("---- End ----");
    }

    public byte[] renderExpandedPageToPdf(String url) {
        System.out.println("---- Starting PDF generation ----");
        System.out.println("URL: " + url);
        
        // Fail fast on bad input
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is blank");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }

        Playwright pw = null;
        Browser browser = null;
        try {
            System.out.println("Creating Playwright instance...");
            pw = Playwright.create();
            
            System.out.println("Launching browser...");
            browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            
            Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setViewportSize(1280, 2000);
            BrowserContext ctx = browser.newContext(ctxOpts);
            Page page = ctx.newPage();

            System.out.println("Navigating to URL...");
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            System.out.println("Dismissing cookie banners and overlays...");
            dismissOverlays(page);

            System.out.println("Expanding page content...");
            expandAll(page);

            // Final settle for any async loads
            page.waitForLoadState(LoadState.NETWORKIDLE);

            System.out.println("Generating PDF...");
            Page.PdfOptions pdf = new Page.PdfOptions()
                .setPrintBackground(true)
                .setFormat("A4")
                .setMargin(new com.microsoft.playwright.options.Margin().setTop("10mm").setBottom("10mm").setLeft("10mm").setRight("10mm"));

            byte[] pdfBytes = page.pdf(pdf);
            System.out.println("PDF generated, size: " + pdfBytes.length + " bytes");
            
            // Close browser and playwright before storing
            System.out.println("Closing browser...");
            ctx.close();
            browser.close();
            pw.close();
            browser = null;
            pw = null;
            
            // Store the PDF to disk with timestamp-based filename
            System.out.println("Storing PDF...");
            String storedFilename = storePdf(pdfBytes);
            
            System.out.println("---- PDF generation completed successfully ----");
            System.out.println("File: " + storedFilename);
            System.out.println("---- End PDF generation ----");
            return pdfBytes;
        } catch (Exception e) {
            System.err.println("---- PDF generation failed with exception ----");
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Exception message: " + e.getMessage());
            e.printStackTrace();
            System.err.println("---- End exception ----");
            throw new RuntimeException("Failed to render PDF: " + e.getMessage(), e);
        } finally {
            // Ensure cleanup
            if (browser != null) {
                try { 
                    System.out.println("Cleanup: closing browser...");
                    browser.close(); 
                } catch (Exception e) {
                    System.err.println("Error closing browser: " + e.getMessage());
                }
            }
            if (pw != null) {
                try { 
                    System.out.println("Cleanup: closing playwright...");
                    pw.close(); 
                } catch (Exception e) {
                    System.err.println("Error closing playwright: " + e.getMessage());
                }
            }
        }
    }

    private String storePdf(byte[] pdfBytes) throws IOException {
        // Generate timestamp-based filename to avoid conflicts
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String filename = "snapshot_" + timestamp + ".pdf";
        Path dest = snapshotDir.resolve(filename);
        
        Files.write(dest, pdfBytes);
        
        System.out.println("==================================================");
        System.out.println("     SNAPSHOT PDF STORED SUCCESSFULLY");
        System.out.println("==================================================");
        System.out.println("Filename:     " + filename);
        System.out.println("Full Path:    " + dest.toAbsolutePath());
        System.out.println("Size:         " + pdfBytes.length + " bytes");
        System.out.println("Exists:       " + Files.exists(dest));
        System.out.println("Readable:     " + Files.isReadable(dest));
        System.out.println("Directory:    " + snapshotDir.toAbsolutePath());
        System.out.println("==================================================");
        
        return filename;
    }

    private void dismissOverlays(Page page) {
        // Wait a bit for cookie banners to appear
        page.waitForTimeout(1500);

        System.out.println("  Looking for cookie consent buttons...");
        
        // Priority 1: Look for accept/allow buttons (most common dismiss buttons)
        String[] primaryTexts = {
            "Accept cookies", "Accept all cookies", "Allow all", "Allow All", 
            "Accept all", "Accept All", "Accept", "Allow"
        };
        boolean dismissed = false;
        
        for (String text : primaryTexts) {
            try {
                // Look for buttons/button-like elements, exclude links to policy pages
                Locator buttons = page.locator(
                    "button:has-text('" + text + "'):visible, " +
                    "[role=button]:has-text('" + text + "'):visible"
                );
                
                if (buttons.count() > 0) {
                    Locator btn = buttons.first();
                    // Make sure it's not a link that navigates away
                    String tagName = (String) btn.evaluate("el => el.tagName.toLowerCase()");
                    String href = null;
                    try {
                        href = (String) btn.evaluate("el => el.getAttribute('href')");
                    } catch (Exception ignored) {}
                    
                    // Only click if it's a button or doesn't have an href
                    if (!tagName.equals("a") || href == null || href.equals("#") || href.isEmpty()) {
                        System.out.println("  Clicking '" + text + "' button");
                        btn.click(new Locator.ClickOptions().setTimeout(1000));
                        page.waitForTimeout(500);
                        dismissed = true;
                        break;
                    } else {
                        System.out.println("  Skipping '" + text + "' - it's a navigation link");
                    }
                }
            } catch (Exception e) {
                System.out.println("  Failed to click '" + text + "': " + e.getMessage());
            }
        }

        // Priority 2: Try the X close button
        if (!dismissed) {
            try {
                System.out.println("  Looking for X close button...");
                Locator closeButtons = page.locator(
                    "button:has-text('×'):visible, " +
                    "button:has-text('✕'):visible, " +
                    "[aria-label*='lose']:visible"
                );
                
                if (closeButtons.count() > 0) {
                    System.out.println("  Clicking close (X) button");
                    closeButtons.first().click(new Locator.ClickOptions().setTimeout(1000));
                    page.waitForTimeout(500);
                    dismissed = true;
                }
            } catch (Exception e) {
                System.out.println("  Failed to click close button: " + e.getMessage());
            }
        }

        // Priority 3: If still not dismissed, try other common buttons in cookie containers
        if (!dismissed) {
            String[] secondaryTexts = {"I agree", "OK", "Got it", "Continue", "Dismiss"};
            for (String text : secondaryTexts) {
                try {
                    // Only look for actual buttons in cookie-related containers
                    Locator buttons = page.locator(
                        ".cookie button:has-text('" + text + "'):visible, " +
                        ".consent button:has-text('" + text + "'):visible, " +
                        "[class*='cookie'] button:has-text('" + text + "'):visible, " +
                        "[id*='cookie'] button:has-text('" + text + "'):visible"
                    );
                    if (buttons.count() > 0) {
                        System.out.println("  Clicking '" + text + "' button in cookie banner");
                        buttons.first().click(new Locator.ClickOptions().setTimeout(1000));
                        page.waitForTimeout(500);
                        dismissed = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Final cleanup: Force remove any remaining fixed/sticky overlays via JavaScript
        System.out.println("  Force removing any remaining overlays via JavaScript...");
        page.evaluate("() => {" +
            "  const overlaySelectors = [" +
            "    '.cookie-banner', '.cookie-consent', '.cookie-notice', '.cookies-overlay'," +
            "    '.gdpr-banner', '.privacy-banner', '.consent-banner'," +
            "    '[class*=\"cookie\"]', '[id*=\"cookie\"]'," +
            "    '[class*=\"consent\"]', '[id*=\"consent\"]'," +
            "    '.modal-backdrop', '[role=\"dialog\"]'" +
            "  ];" +
            "  overlaySelectors.forEach(selector => {" +
            "    document.querySelectorAll(selector).forEach(el => {" +
            "      const style = window.getComputedStyle(el);" +
            "      if (style.position === 'fixed' || style.position === 'sticky' || style.position === 'absolute') {" +
            "        const zIndex = parseInt(style.zIndex) || 0;" +
            "        if (zIndex > 100) {" + // Only remove high z-index overlays
            "          console.log('Removing overlay:', el.className, el.id);" +
            "          el.style.display = 'none';" +
            "          el.remove();" +
            "        }" +
            "      }" +
            "    });" +
            "  });" +
            "  document.body.style.overflow = 'auto';" +
            "  document.documentElement.style.overflow = 'auto';" +
            "}");

        page.waitForTimeout(500);
        System.out.println("  Overlay dismissal complete (dismissed: " + dismissed + ")");
    }

    private void expandAll(Page page) {
        System.out.println("Expanding: Opening all <details> elements...");
        page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)");

        System.out.println("Expanding: Force-expanding accordions via JavaScript...");
        // Use JavaScript to expand content WITHOUT clicking (avoids triggering navigation)
        page.evaluate("() => {" +
            "  document.querySelectorAll('[aria-expanded=\"false\"]').forEach(el => {" +
            "    if (!el.closest('nav, header, .nav, .navbar, .menu')) {" +
            "      el.setAttribute('aria-expanded', 'true');" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('[aria-hidden=\"true\"]').forEach(el => {" +
            "    if (!el.closest('nav, header, .nav, .navbar, .menu')) {" +
            "      el.setAttribute('aria-hidden', 'false');" +
            "      el.style.display = 'block';" +
            "      el.style.visibility = 'visible';" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('.collapse').forEach(el => {" +
            "    if (!el.closest('nav, header, .nav, .navbar, .menu')) {" +
            "      el.classList.add('show');" +
            "      el.style.display = 'block';" +
            "    }" +
            "  });" +
            "  document.querySelectorAll('.hidden, [hidden]').forEach(el => {" +
            "    if (!el.closest('nav, header, .nav, .navbar, .menu, script, style')) {" +
            "      el.classList.remove('hidden');" +
            "      el.removeAttribute('hidden');" +
            "      el.style.display = 'block';" +
            "    }" +
            "  });" +
            "}");

        page.waitForTimeout(500);

        System.out.println("Expanding: Scrolling page...");
        autoScroll(page);

        System.out.println("Expanding: Selectively clicking content expand buttons...");
        // Only click buttons in main content area, avoid navigation
        String[] expandTexts = {
            "Show more", "See more", "Read more", "Expand all", "View more"
        };
        
        for (String text : expandTexts) {
            try {
                // More specific selector: only buttons in main/article/section, not in nav/header
                Locator buttons = page.locator("main button:has-text('" + text + "'), " +
                                               "article button:has-text('" + text + "'), " +
                                               "section button:has-text('" + text + "'), " +
                                               ".content button:has-text('" + text + "')");
                int count = Math.min(buttons.count(), 10);
                System.out.println("  Found " + count + " '" + text + "' buttons in content area");
                for (int i = 0; i < count; i++) {
                    try {
                        Locator btn = buttons.nth(i);
                        if (btn.isVisible()) {
                            btn.click(new Locator.ClickOptions().setTimeout(300).setForce(true));
                            page.waitForTimeout(300);
                        }
                    } catch (PlaywrightException ignored) {}
                }
            } catch (Exception ignored) {}
        }

        System.out.println("Expanding: Final visibility pass (preserving navigation)...");
        // Make content visible but keep navigation as-is
        page.evaluate("() => {" +
            "  const mainContent = document.querySelector('main, article, #content, .content, [role=\"main\"]');" +
            "  const elementsToShow = mainContent ? mainContent.querySelectorAll('*') : document.querySelectorAll('main *, article *, section *');" +
            "  elementsToShow.forEach(el => {" +
            "    if (el.closest('script, style, noscript, nav, header, .nav, .navbar')) return;" +
            "    const style = window.getComputedStyle(el);" +
            "    if (style.display === 'none') {" +
            "      el.style.display = 'block';" +
            "    }" +
            "    if (style.visibility === 'hidden') {" +
            "      el.style.visibility = 'visible';" +
            "    }" +
            "  });" +
            "}");

        page.waitForTimeout(1000);
        System.out.println("Expanding: Complete");
    }

    private void autoScroll(Page page) {
        page.evaluate("async () => {" +
            "  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));" +
            "  for (let i = 0; i < 5; i++) {" +
            "    window.scrollBy(0, window.innerHeight);" +
            "    await delay(300);" +
            "  }" +
            "  window.scrollTo(0, document.body.scrollHeight);" +
            "  await delay(500);" +
            "  window.scrollTo(0, 0);" +
            "}");
        page.waitForTimeout(1500);
    }
}