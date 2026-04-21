package com.drs.agent.repository;

import com.drs.agent.model.DiagnosisSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Diagnosis Session Repository
 *
 * Provides data access for diagnosis sessions.
 */
@Repository
public interface DiagnosisSessionRepository extends JpaRepository<DiagnosisSession, Long>, JpaSpecificationExecutor<DiagnosisSession> {

    Optional<DiagnosisSession> findBySessionId(String sessionId);

    List<DiagnosisSession> findByUserId(String userId);

    List<DiagnosisSession> findByProblemType(String problemType);

    List<DiagnosisSession> findByStatus(String status);

    Page<DiagnosisSession> findByUserId(String userId, Pageable pageable);

    Page<DiagnosisSession> findByProblemType(String problemType, Pageable pageable);

    @Query("SELECT d FROM DiagnosisSession d WHERE d.userId = :userId AND d.createdAt BETWEEN :startDate AND :endDate")
    List<DiagnosisSession> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT d FROM DiagnosisSession d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    List<DiagnosisSession> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT d.problemType, COUNT(d) FROM DiagnosisSession d GROUP BY d.problemType")
    List<Object[]> countByProblemType();

    @Query("SELECT COUNT(d) FROM DiagnosisSession d WHERE d.createdAt >= :startDate")
    Long countAfterDate(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(d.confidenceScore) FROM DiagnosisSession d WHERE d.confidenceScore IS NOT NULL")
    Double getAverageConfidenceScore();

    @Query("SELECT COUNT(d) FROM DiagnosisSession d WHERE d.isCorrect = true")
    Long countCorrectDiagnoses();

    void deleteBySessionId(String sessionId);
}