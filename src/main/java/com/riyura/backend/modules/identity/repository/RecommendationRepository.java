package com.riyura.backend.modules.identity.repository;

import com.riyura.backend.modules.identity.model.Recommendation;
import com.riyura.backend.modules.identity.model.RecommendationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, RecommendationId> {
    List<Recommendation> findByUserIdOrderByGeneratedAtDesc(UUID userId);

    // Bulk JPQL DELETE — avoids the load-then-remove pattern of derived deletes.
    // clearAutomatically = true flushes the EntityManager cache so the subsequent
    // saveAll() starts with a clean session.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM Recommendation r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
