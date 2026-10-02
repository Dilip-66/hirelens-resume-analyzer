package com.resumerag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();
    private Supabase supabase = new Supabase();
    private Llm llm = new Llm();
    private Rag rag = new Rag();
    private Ai ai = new Ai();

    public static class Cors {
        private String allowedOrigins;
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Supabase {
        private String url;
        private String jwtSecret;
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    }

    /**
     * Talks to Ollama's OpenAI-compatible surface ({@code /v1/chat/completions},
     * {@code /v1/embeddings}). No API key is required for a local daemon, so
     * apiKey is optional and the Authorization header is only sent when set.
     */
    public static class Llm {
        private String baseUrl = "http://localhost:11434/v1";
        private String apiKey;
        private String embeddingModel = "nomic-embed-text";
        private int embeddingDimensions = 768;
        private String chatModel = "qwen2.5:7b";
        private int chatTimeoutSeconds = 300;
        private int embeddingTimeoutSeconds = 120;
        private int numCtx = 8192;
        private boolean jsonMode = true;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public int getEmbeddingDimensions() { return embeddingDimensions; }
        public void setEmbeddingDimensions(int embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
        public String getChatModel() { return chatModel; }
        public void setChatModel(String chatModel) { this.chatModel = chatModel; }
        public int getChatTimeoutSeconds() { return chatTimeoutSeconds; }
        public void setChatTimeoutSeconds(int chatTimeoutSeconds) { this.chatTimeoutSeconds = chatTimeoutSeconds; }
        public int getEmbeddingTimeoutSeconds() { return embeddingTimeoutSeconds; }
        public void setEmbeddingTimeoutSeconds(int embeddingTimeoutSeconds) { this.embeddingTimeoutSeconds = embeddingTimeoutSeconds; }
        public int getNumCtx() { return numCtx; }
        public void setNumCtx(int numCtx) { this.numCtx = numCtx; }
        public boolean isJsonMode() { return jsonMode; }
        public void setJsonMode(boolean jsonMode) { this.jsonMode = jsonMode; }
    }

    public static class Rag {
        private int chunkSize;
        private int chunkOverlap;
        private int topK;
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }

    /**
     * AI Assistance settings. The system prompt lives here rather than in a
     * string literal so it can be tuned, audited and overridden per environment
     * without touching Java, and so the assistant and the analysis pipeline
     * clearly declare whose instructions they are running.
     */
    public static class Ai {
        private String systemPrompt = "";
        /** Resume chunks retrieved per assistant question. */
        private int retrievalTopK = 4;
        /** Job-description passages selected per question. */
        private int jobPassageLimit = 3;
        /** Prior conversation turns carried into a follow-up question. */
        private int maxHistoryTurns = 4;
        /** Hard ceiling on a submitted question, to bound prompt growth. */
        private int maxQuestionChars = 500;
        private int timeoutSeconds = 180;
        private double temperature = 0.3;

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public int getRetrievalTopK() { return retrievalTopK; }
        public void setRetrievalTopK(int retrievalTopK) { this.retrievalTopK = retrievalTopK; }
        public int getJobPassageLimit() { return jobPassageLimit; }
        public void setJobPassageLimit(int jobPassageLimit) { this.jobPassageLimit = jobPassageLimit; }
        public int getMaxHistoryTurns() { return maxHistoryTurns; }
        public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
        public int getMaxQuestionChars() { return maxQuestionChars; }
        public void setMaxQuestionChars(int maxQuestionChars) { this.maxQuestionChars = maxQuestionChars; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
    }

    public Cors getCors() { return cors; }
    public Supabase getSupabase() { return supabase; }
    public Llm getLlm() { return llm; }
    public Rag getRag() { return rag; }
    public Ai getAi() { return ai; }
}
