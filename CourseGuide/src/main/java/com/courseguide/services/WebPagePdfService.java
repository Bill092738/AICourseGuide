package com.courseguide.services;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebPagePdfService {

    public byte[] renderExpandedPageToPdf(String url) {
        // Fail fast on bad input
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is blank");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Browser.NewContextOptions ctxOpts = new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true)
                .setViewportSize(1280, 2000);
            BrowserContext ctx = browser.newContext(ctxOpts);
            Page page = ctx.newPage();

            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            expandAll(page);

            // Final settle for any async loads
            page.waitForLoadState(LoadState.NETWORKIDLE);

            Page.PdfOptions pdf = new Page.PdfOptions()
                .setPrintBackground(true)
                .setFormat("A4")
                .setMargin(new com.microsoft.playwright.options.Margin().setTop("10mm").setBottom("10mm").setLeft("10mm").setRight("10mm")); // sets all margins to 10mm

            return page.pdf(pdf);
        }
    }

    private void expandAll(Page page) {
        // Open all <details>
        page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)");

        // Click common expand controls until nothing left to expand
        boolean changed;
        int passes = 0;
        do {
            changed = false;
            passes++;

            // Scroll to trigger lazy loading
            autoScroll(page);

            // Click buttons/links with common labels
            for (String text : List.of("Show more", "See more", "Read more", "More", "Expand", "Load more")) {
                Locator l = page.locator("button:has-text('" + text + "'), [role=button]:has-text('" + text + "'), a:has-text('" + text + "')");
                int count = l.count();
                for (int i = 0; i < count; i++) {
                    try {
                        l.nth(i).click(new Locator.ClickOptions().setTimeout(1000));
                        page.waitForTimeout(300);
                        changed = true;
                    } catch (PlaywrightException ignored) {}
                }
            }

            // Click any collapsed aria-expanded toggles
            Locator toggles = page.locator("[aria-expanded='false']");
            int tcount = Math.min(toggles.count(), 50);
            for (int i = 0; i < tcount; i++) {
                try {
                    toggles.nth(i).click(new Locator.ClickOptions().setTimeout(1000));
                    page.waitForTimeout(200);
                    changed = true;
                } catch (PlaywrightException ignored) {}
            }

            // Re-open details if any collapsed again
            page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)");

            page.waitForLoadState(LoadState.NETWORKIDLE);
        } while (changed && passes < 10);
    }

    private void autoScroll(Page page) {
        page.evaluate("async () => {" +
            "  const sleep = ms => new Promise(r => setTimeout(r, ms));" +
            "  let last = 0, same = 0;" +
            "  for (let i = 0; i < 40; i++) {" +
            "    window.scrollBy(0, window.innerHeight);" +
            "    await sleep(250);" +
            "    const h = document.body.scrollHeight;" +
            "    if (h === last) {" +
            "      same++;" +
            "      if (same >= 3) break;" +
            "    } else { last = h; same = 0; }" +
            "  }" +
            "  window.scrollTo(0, 0);" +
            "}");
    }
}