package com.courseguide;

import org.springframework.web.bind.annotation.*;

/**
 * Original API controller - now deprecated.
 * Use the specific controllers instead:
 * - {@link com.courseguide.controllers.RecommendationController} for recommendation endpoints
 * - {@link com.courseguide.controllers.CourseSelectionController} for course selection
 * - {@link com.courseguide.controllers.FileUploadController} for file uploads
 * - {@link com.courseguide.controllers.SnapshotController} for snapshot PDF generation
 * 
 * This class is kept for backwards compatibility only.
 */
@Deprecated
@RestController
@RequestMapping("/api")
public class ApiController {
    // This controller has been refactored into smaller, focused controllers.
    // See the specific controllers for the actual endpoint implementations.
}
