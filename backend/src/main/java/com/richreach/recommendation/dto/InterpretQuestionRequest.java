package com.richreach.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterpretQuestionRequest(@NotBlank @Size(max = 200) String question) {
}
