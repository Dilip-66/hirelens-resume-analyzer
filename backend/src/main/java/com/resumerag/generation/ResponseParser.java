package com.resumerag.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);
    private final ObjectMapper objectMapper;

    public ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResult parse(String jsonContent) {
        try {
            return objectMapper.readValue(jsonContent, AnalysisResult.class);
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response as AnalysisResult: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse analysis response", e);
        }
    }
}
