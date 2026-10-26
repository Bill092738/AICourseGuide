package com.courseguide.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record UserBasicInfo(
        @JsonAlias("university") String university,
        @JsonAlias("major") String major,
        @JsonAlias({"degree_level", "degreeLevel"}) DegreeLevel degreeLevel,
        @JsonAlias({"graduation_year", "graduationYear"}) Integer graduationYear
) {}