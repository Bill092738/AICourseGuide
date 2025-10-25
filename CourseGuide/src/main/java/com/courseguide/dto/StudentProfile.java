package com.courseguide.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record StudentProfile(
        @JsonAlias({"user", "user_basic_info", "userBasicInfo"}) UserBasicInfo user,
        @JsonAlias({"target", "target_goal", "targetGoal"}) TargetGoal target,
        // ID returned by /api/upload-progress for the temp-stored PDF
        @JsonAlias({"progress_file_id", "progressFileId"}) String progressFileId
) {}