package com.releasepilot.featureflag.repository;
import com.releasepilot.featureflag.entity.FeatureFlag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    List<FeatureFlag> findAllByProject_IdOrderByIdAsc(Long projectId);
    Optional<FeatureFlag> findByIdAndProject_Id(Long id, Long projectId);
    boolean existsByProject_IdAndKey(Long projectId, String key);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FeatureFlag f where f.project.id = :project")
    int deleteForProject(@Param("project") Long project);
}
