package com.courseguide.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record TargetGoal(
        @JsonAlias("major") String major,
        @JsonAlias("minor") String minor,
        @JsonAlias({"plan_name", "planName"}) String planName,
        @JsonAlias({"preferred_electives", "preferredElectives"}) List<String> preferredElectives,
        @JsonAlias("constraints") Constraints constraints
) {}