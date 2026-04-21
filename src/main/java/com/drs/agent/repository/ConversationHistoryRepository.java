package com.drs.agent.repository;

import com.drs.agent.model.ConversationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Conversation History Repository
 *
 * Provides data access for conversation history records.
 */
@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, Long> {

    List<ConversationHistory> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ConversationHistory> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    void deleteBySessionId(String sessionId);
}