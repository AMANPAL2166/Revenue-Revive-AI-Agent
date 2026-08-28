package com.reviveai.ai;

/**
 * The one and only abstraction ReviveAI uses to talk to an LLM. Nothing
 * outside this interface (and its single implementation) knows which
 * provider is behind it, what the HTTP shape looks like, or holds the API
 * key. AgentService depends only on this interface, never on AiClientImpl
 * directly, so swapping providers means writing a new implementation class
 * — no changes anywhere else.
 */
public interface AiClient {

    /**
     * Sends a system prompt and a user prompt to the LLM and returns its
     * raw text response verbatim (not yet parsed or validated — that is
     * AgentService's job). Implementations should throw on any failure
     * (network error, timeout, non-2xx response, unexpected response
     * shape) rather than returning a partial or empty string; AgentService
     * treats any thrown exception as "the AI call failed" and falls back
     * to safe behavior.
     */
    String complete(String systemPrompt, String userPrompt);
}
