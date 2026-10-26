package com.courseguide.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record LlmConfig(
        @JsonAlias({"api_base_url", "apiBaseUrl"}) String apiBaseUrl,
        @JsonAlias({"api_key", "apiKey"}) String apiKey,
        @JsonAlias({"model_name", "modelName"}) String modelName
) {}
