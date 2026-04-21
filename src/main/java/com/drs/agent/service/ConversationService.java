package com.drs.agent.service;

import com.drs.agent.model.ConversationHistory;
import com.drs.agent.repository.ConversationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Conversation Service
 *
 * Handles conversation history management for the AI agent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationHistoryRepository conversationHistoryRepository;

    @Transactional
    public ConversationHistory saveMessage(String sessionId, String role, String content, Integer tokensUsed) {
        ConversationHistory history = ConversationHistory.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .tokensUsed(tokensUsed)
                .build();
        return conversationHistoryRepository.save(history);
    }

    public List<ConversationHistory> getConversationHistory(String sessionId) {
        return conversationHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void clearConversationHistory(String sessionId) {
        conversationHistoryRepository.deleteBySessionId(sessionId);
    }
}