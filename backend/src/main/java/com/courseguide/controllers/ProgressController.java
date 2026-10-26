package com.courseguide.controllers;

import com.courseguide.services.UserProgressService;
import com.courseguide.services.UserProgressService.UserProgress;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Controller for user progress save/restore endpoints.
 * Supports both local (SQL) and online (Supabase) storage modes.
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private UserProgressService userProgressService;

    /**
     * Generates a new unique case number.
     *
     * @return a map containing the new case number
     */
    @PostMapping("/case-number")
    public Map<String, String> generateCaseNumber() {
        String caseNumber = userProgressService.generateCaseNumber();
        return Map.of("caseNumber", caseNumber);
    }

    /**
     * Saves user progress locally (SQL).
     *
     * @param progress the user progress to save
     * @return a map containing the case number and status
     */
    @PostMapping("/save/local")
    public Map<String, Object> saveProgressLocal(@RequestBody UserProgress progress) {
        try {
            String caseNumber = userProgressService.saveProgressLocal(progress);
            return Map.of(
                "status", "saved",
                "caseNumber", caseNumber,
                "mode", "local"
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save progress: " + e.getMessage());
        }
    }

    /**
     * Saves user progress online (Supabase) with local fallback.
     *
     * @param progress the user progress to save
     * @return a map containing the case number, status, and whether it was synced
     */
    @PostMapping("/save/online")
    public Map<String, Object> saveProgressOnline(@RequestBody UserProgress progress) {
        try {
            String caseNumber = userProgressService.saveProgressOnline(progress);
            boolean isSynced = userProgressService.loadProgressLocal(caseNumber) != null 
                && userProgressService.loadProgressLocal(caseNumber).isSyncedToCloud();
            return Map.of(
                "status", "saved",
                "caseNumber", caseNumber,
                "mode", "online",
                "synced", isSynced
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save progress: " + e.getMessage());
        }
    }

    /**
     * Loads user progress by case number (uses specified mode).
     *
     * @param caseNumber the case number to load
     * @param mode the storage mode ("local" or "online")
     * @return the user progress
     */
    @GetMapping("/load/{caseNumber}")
    public UserProgress loadProgress(@PathVariable String caseNumber, @RequestParam(defaultValue = "local") String mode) {
        UserProgress progress;
        if ("online".equals(mode)) {
            progress = userProgressService.loadProgressOnline(caseNumber);
        } else {
            progress = userProgressService.loadProgressLocal(caseNumber);
        }
        
        if (progress == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No progress found for case number: " + caseNumber);
        }
        return progress;
    }

    /**
     * Lists all saved progress entries.
     *
     * @return list of progress summaries
     */
    @GetMapping("/list")
    public List<UserProgress> listProgress() {
        return userProgressService.listAllProgressLocal();
    }

    /**
     * Deletes user progress by case number.
     *
     * @param caseNumber the case number to delete
     * @return a map containing the deletion status
     */
    @DeleteMapping("/delete/{caseNumber}")
    public Map<String, Object> deleteProgress(@PathVariable String caseNumber) {
        boolean deleted = userProgressService.deleteProgressLocal(caseNumber);
        if (deleted) {
            return Map.of("status", "deleted", "caseNumber", caseNumber);
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No progress found for case number: " + caseNumber);
        }
    }

    /**
     * Syncs all unsynced local progress to online storage.
     *
     * @return a map containing sync results
     */
    @PostMapping("/sync")
    public Map<String, Object> syncToOnline() {
        try {
            int unsyncedCount = userProgressService.getUnsyncedCount();
            int syncedCount = userProgressService.syncLocalToOnline();
            return Map.of(
                "status", "synced",
                "totalUnsynced", unsyncedCount,
                "syncedCount", syncedCount,
                "remaining", unsyncedCount - syncedCount
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sync failed: " + e.getMessage());
        }
    }

    /**
     * Gets sync status information.
     *
     * @return a map containing sync status
     */
    @GetMapping("/sync/status")
    public Map<String, Object> getSyncStatus() {
        int unsyncedCount = userProgressService.getUnsyncedCount();
        return Map.of(
            "unsyncedCount", unsyncedCount,
            "needsSync", unsyncedCount > 0
        );
    }
}
