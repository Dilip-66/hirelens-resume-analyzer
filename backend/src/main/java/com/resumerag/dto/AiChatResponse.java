package com.resumerag.dto;

import java.util.List;
import java.util.UUID;

/**
 * An assistant turn as returned to the client.
 *
 * <p>{@code grounded} tells the UI whether the answer was produced with a real
 * resume/analysis behind it, so general advice is never presented as if it were
 * personalised.
 */
public record AiChatResponse(
        UUID analysisId,
        String answer,
        List<String> suggestedFollowUps,
        List<AiSource> sources,
        boolean grounded
) {}
