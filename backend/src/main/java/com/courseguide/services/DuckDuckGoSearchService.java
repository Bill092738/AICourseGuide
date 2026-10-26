package com.courseguide.services;

import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Service for resolving DuckDuckGo search URLs.
 * Uses DuckDuckGo Instant Answer API with HTML fallback.
 */
@Service
public class DuckDuckGoSearchService {

    /**
     * Resolves a search query to the first DuckDuckGo result URL.
     * Tries Instant Answer JSON first, then HTML results page.
     *
     * @param query the search query
     * @return Optional containing the resolved URL, or empty if not found
     */
    public Optional<String> resolveSearchUrl(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        Optional<String> viaIa = resolveFirstDuckDuckGoUrl(query);
        if (viaIa.isPresent()) return viaIa;
        return resolveFirstDuckDuckGoHtmlResult(query);
    }

    /**
     * Resolves the first DuckDuckGo result URL using Instant Answer JSON API.
     *
     * @param query the search query
     * @return Optional containing the first result URL, or empty if not found
     */
    public Optional<String> resolveFirstDuckDuckGoUrl(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&no_redirect=1&skip_disambig=1";

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json");
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> response = resp.getBody();
            if (response == null) return Optional.empty();

            Object redirect = response.get("Redirect");
            if (redirect instanceof String s && isValidHttpUrl(s) && !s.isBlank()) return Optional.of(s);

            Object abstractUrl = response.get("AbstractURL");
            if (abstractUrl instanceof String s && isValidHttpUrl(s) && !s.isBlank()) return Optional.of(s);

            Object results = response.get("Results");
            if (results instanceof List<?> resList) {
                for (Object r : resList) {
                    if (r instanceof Map<?, ?> res) {
                        Object first = res.get("FirstURL");
                        if (first instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                    }
                }
            }

            Object related = response.get("RelatedTopics");
            if (related instanceof List<?> topics) {
                for (Object t : topics) {
                    if (t instanceof Map<?, ?> topic) {
                        Object firstUrl = topic.get("FirstURL");
                        if (firstUrl instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                        Object sub = topic.get("Topics");
                        if (sub instanceof List<?> subList) {
                            for (Object sObj : subList) {
                                if (sObj instanceof Map<?, ?> subTopic) {
                                    Object nestedUrl = subTopic.get("FirstURL");
                                    if (nestedUrl instanceof String s && isValidHttpUrl(s)) return Optional.of(s);
                                }
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("DuckDuckGo IA resolve failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fallback: fetches HTML results page and parses the first result URL.
     *
     * @param query the search query
     * @return Optional containing the first result URL, or empty if not found
     */
    private Optional<String> resolveFirstDuckDuckGoHtmlResult(String query) {
        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36")
                .referrer("https://duckduckgo.com/")
                .timeout(8000)
                .get();

            for (Element a : doc.select("a.result__a[href], a.result__url[href]")) {
                String href = a.attr("href");
                String real = decodeDuckDuckGoRedirect(href);
                if (isValidHttpUrl(real)) return Optional.of(real);
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("DuckDuckGo HTML resolve failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decodes DuckDuckGo redirect links to the real target URL.
     *
     * @param href the redirect URL from DuckDuckGo
     * @return the decoded target URL, or empty string if invalid
     */
    private static String decodeDuckDuckGoRedirect(String href) {
        if (href == null || href.isBlank()) return "";
        try {
            int q = href.indexOf('?');
            if (q >= 0 && href.contains("uddg=")) {
                String qs = href.substring(q + 1);
                for (String part : qs.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String k = part.substring(0, eq);
                        String v = part.substring(eq + 1);
                        if ("uddg".equals(k)) {
                            return URLDecoder.decode(v, StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            return href;
        } catch (Exception ex) {
            return "";
        }
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
