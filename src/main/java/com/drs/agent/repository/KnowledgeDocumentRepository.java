package com.drs.agent.repository;

import com.drs.agent.model.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Knowledge Document Repository
 *
 * Provides data access for knowledge base documents.
 */
@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Optional<KnowledgeDocument> findByDocumentId(String documentId);

    Optional<KnowledgeDocument> findByContentHash(String contentHash);

    void deleteByDocumentId(String documentId);
}