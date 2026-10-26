package com.courseguide.services;

import com.courseguide.services.UserProgressService.UserProgress;
import com.courseguide.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProgressServiceTest {

    private JdbcTemplate jdbc;
    private SupabaseService supabase;
    private UserProgressService service;

    @BeforeEach
    void setUp() {
        jdbc = TestDatabase.newJdbcTemplate();
        supabase = mock(SupabaseService.class);
        service = new UserProgressService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "supabaseService", supabase);
        service.initializeTable();
    }

    private static UserProgress progress(String caseNumber, String form) {
        return new UserProgress(caseNumber, form, "", "[]", "0");
    }

    private boolean syncedFlag(String caseNumber) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT synced_to_cloud FROM user_progress WHERE case_number = ?", Boolean.class, caseNumber));
    }

    @Test
    void generatesEightCharacterUppercaseAlphanumericCaseNumbers() {
        for (int i = 0; i < 50; i++) {
            assertThat(service.generateCaseNumber()).matches("[A-Z0-9]{8}");
        }
    }

    @Test
    void saveLocalGeneratesCaseNumberWhenMissing() {
        String cn = service.saveProgressLocal(progress(null, "{\"major\":\"CS\"}"));

        assertThat(cn).matches("[A-Z0-9]{8}");
        UserProgress loaded = service.loadProgressLocal(cn);
        assertThat(loaded.getFormData()).isEqualTo("{\"major\":\"CS\"}");
        assertThat(loaded.getCreatedAt()).isNotBlank();
        assertThat(loaded.isSyncedToCloud()).isFalse();
    }

    @Test
    void saveLocalUpdatesExistingCaseNumber() {
        service.saveProgressLocal(progress("ABCD1234", "first"));
        service.saveProgressLocal(progress("ABCD1234", "second"));

        assertThat(service.loadProgressLocal("ABCD1234").getFormData()).isEqualTo("second");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_progress", Integer.class)).isEqualTo(1);
    }

    @Test
    void loadLocalReturnsNullWhenNotFound() {
        assertThat(service.loadProgressLocal("NOPE0000")).isNull();
    }

    @Test
    void listAndDeleteLocal() {
        service.saveProgressLocal(progress("AAAA1111", "a"));
        service.saveProgressLocal(progress("BBBB2222", "b"));

        assertThat(service.listAllProgressLocal()).extracting(UserProgress::getCaseNumber)
                .containsExactlyInAnyOrder("AAAA1111", "BBBB2222");

        assertThat(service.deleteProgressLocal("AAAA1111")).isTrue();
        assertThat(service.deleteProgressLocal("AAAA1111")).isFalse();
        assertThat(service.listAllProgressLocal()).extracting(UserProgress::getCaseNumber).containsExactly("BBBB2222");
    }

    @Test
    void saveOnlineUsesSupabaseWhenAvailable() {
        when(supabase.saveProgress(any())).thenReturn("ONLINE01");

        assertThat(service.saveProgressOnline(progress("ONLINE01", "x"))).isEqualTo("ONLINE01");
        assertThat(service.loadProgressLocal("ONLINE01")).isNull();
    }

    @Test
    void saveOnlineFallsBackToLocalAndMarksUnsynced() {
        when(supabase.saveProgress(any())).thenThrow(new RuntimeException("Supabase is not configured"));

        String cn = service.saveProgressOnline(progress("FALLBK01", "x"));

        assertThat(cn).isEqualTo("FALLBK01");
        assertThat(service.loadProgressLocal(cn)).isNotNull();
        assertThat(syncedFlag(cn)).isFalse();
        assertThat(service.getUnsyncedCount()).isEqualTo(1);
    }

    @Test
    void loadOnlinePrefersSupabaseThenFallsBackToLocal() {
        UserProgress remote = progress("REMOTE01", "remote");
        when(supabase.loadProgress("REMOTE01")).thenReturn(remote);
        when(supabase.loadProgress("LOCAL001")).thenThrow(new RuntimeException("offline"));
        service.saveProgressLocal(progress("LOCAL001", "local"));

        assertThat(service.loadProgressOnline("REMOTE01")).isSameAs(remote);
        assertThat(service.loadProgressOnline("LOCAL001").getFormData()).isEqualTo("local");
    }

    @Test
    void syncUploadsUnsyncedRecordsAndMarksThemSynced() {
        service.saveProgressLocal(progress("SYNC0001", "a"));
        service.saveProgressLocal(progress("SYNC0002", "b"));
        when(supabase.saveProgress(argThat(p -> p != null && p.getCaseNumber().equals("SYNC0002"))))
                .thenThrow(new RuntimeException("timeout"));

        int synced = service.syncLocalToOnline();

        assertThat(synced).isEqualTo(1);
        assertThat(syncedFlag("SYNC0001")).isTrue();
        assertThat(syncedFlag("SYNC0002")).isFalse();
        assertThat(service.getUnsyncedCount()).isEqualTo(1);
        verify(supabase, times(2)).saveProgress(any());
    }
}
