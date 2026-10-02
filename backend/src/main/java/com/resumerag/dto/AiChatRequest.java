package com.resumerag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One assistant turn.
 *
 * <p>No resume text is accepted from the client. The backend resolves everything
 * from {@code analysisId} and its own ownership checks, so a caller cannot point
 * the assistant at another user's documents, and cannot smuggle content past
 * the retrieval step.
 */
public record AiChatRequest(
        @NotBlank(message = "question is required")
        @Size(max = 500, message = "question must be 500 characters or fewer")
        String question,

        /**
         * Optional. When supplied it must belong to the caller; when absent the
         * assistant answers in general mode, which the UI labels differently.
         */
        UUID analysisId,

        /** Prior turns, oldest first. Server-side capped. */
        List<ChatTurn> history
) {
    public record ChatTurn(String role, String content) {
        public boolean isUser() {
            return "user".equalsIgnoreCase(role);
        }
    }
}
