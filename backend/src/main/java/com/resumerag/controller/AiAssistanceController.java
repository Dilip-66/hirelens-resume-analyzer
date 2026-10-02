package com.resumerag.controller;

import com.resumerag.assistant.QuestionCatalog;
import com.resumerag.dto.AiChatRequest;
import com.resumerag.dto.AiChatResponse;
import com.resumerag.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * AI Assistance.
 *
 * <p>Under {@code /api/**}, which SecurityConfig already requires to be
 * authenticated, so no extra rule is needed. The service performs the
 * ownership checks that stop one user asking questions about another's report.
 */
@RestController
@RequestMapping("/api/ai-assistance")
public class AiAssistanceController {

    private final com.resumerag.assistant.AiAssistantService aiAssistantService;
    private final QuestionCatalog questionCatalog;
    private final CurrentUser currentUser;

    public AiAssistanceController(com.resumerag.assistant.AiAssistantService aiAssistantService,
                                  QuestionCatalog questionCatalog,
                                  CurrentUser currentUser) {
        this.aiAssistantService = aiAssistantService;
        this.questionCatalog = questionCatalog;
        this.currentUser = currentUser;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiAssistantService.ask(currentUser.id(), request);
    }

    /**
     * Suggested questions. Served from the backend so the catalogue stays in one
     * auditable place instead of being duplicated in the client, and so the same
     * list can be used to validate model-proposed follow-ups.
     */
    @GetMapping("/questions")
    public List<QuestionCatalog.QuestionCategory> questions() {
        return questionCatalog.categories();
    }
}
