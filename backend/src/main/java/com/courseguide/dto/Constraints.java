package com.courseguide.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Constraints(
        @JsonAlias({"max_credit_hour", "maxCreditHour"}) Integer maxCreditHour,
        @JsonAlias("semester") String semester
) {}