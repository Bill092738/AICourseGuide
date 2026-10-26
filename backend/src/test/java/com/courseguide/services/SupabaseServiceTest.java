package com.courseguide.services;

import com.courseguide.services.UserProgressService.UserProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseServiceTest {

    private static final String URL = "https://demo.supabase.co";

    private SupabaseService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        service = new SupabaseService();
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        server = MockRestServiceServer.bindTo(rest).build();
    }

    private void configure() {
        ReflectionTestUtils.setField(service, "supabaseUrl", URL);
        ReflectionTestUtils.setField(service, "supabaseAnonKey", "anon");
    }

    @Test
    void unconfiguredServiceRefusesCalls() {
        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.saveProgress(new UserProgress())).hasMessageContaining("not configured");
        assertThatThrownBy(() -> service.loadProgress("X")).hasMessageContaining("not configured");
        assertThatThrownBy(() -> service.deleteProgress("X")).hasMessageContaining("not configured");
    }

    @Test
    void savePostsRowWithAuthHeaders() {
        configure();
        server.expect(requestTo(URL + "/rest/v1/user_progress"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "anon"))
                .andExpect(header("Authorization", "Bearer anon"))
                .andExpect(jsonPath("$.case_number").value("ABCD1234"))
                .andExpect(jsonPath("$.synced_to_cloud").value(true))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThat(service.saveProgress(new UserProgress("ABCD1234", "{}", "", "[]", "0"))).isEqualTo("ABCD1234");
        server.verify();
    }

    @Test
    void saveWrapsServerErrors() {
        configure();
        server.expect(requestTo(URL + "/rest/v1/user_progress")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.saveProgress(new UserProgress("A", "", "", "", ""))).hasMessageContaining("Failed to save");
    }

    @Test
    void loadMapsFirstRowAndReturnsNullWhenEmpty() {
        configure();
        server.expect(requestTo(URL + "/rest/v1/user_progress?case_number=eq.ABCD1234"))
                .andRespond(withSuccess("""
                        [{"case_number":"ABCD1234","form_data":"{}","selected_courses":"[]","total_credits":"9"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/rest/v1/user_progress?case_number=eq.NONE0000"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        UserProgress p = service.loadProgress("ABCD1234");
        assertThat(p.getCaseNumber()).isEqualTo("ABCD1234");
        assertThat(p.getTotalCredits()).isEqualTo("9");
        assertThat(p.isSyncedToCloud()).isTrue();
        assertThat(service.loadProgress("NONE0000")).isNull();
    }

    @Test
    void deleteReturnsTrueOnSuccess() {
        configure();
        server.expect(requestTo(URL + "/rest/v1/user_progress?case_number=eq.ABCD1234"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.deleteProgress("ABCD1234")).isTrue();
    }
}
