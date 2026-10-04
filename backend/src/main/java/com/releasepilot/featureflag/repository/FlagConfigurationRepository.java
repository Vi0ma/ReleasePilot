package com.releasepilot.featureflag.repository;
import com.releasepilot.featureflag.entity.FlagConfiguration;
import com.releasepilot.evaluation.domain.EvaluationConfiguration;
import com.releasepilot.cache.CacheAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FlagConfigurationRepository extends JpaRepository<FlagConfiguration, Long> {
    List<FlagConfiguration> findAllByFeatureFlag_IdOrderByEnvironment_IdAsc(Long flagId);
    Optional<FlagConfiguration> findByFeatureFlag_IdAndEnvironment_Id(Long flagId, Long environmentId);
    @Query("select new com.releasepilot.evaluation.domain.EvaluationConfiguration(c.enabled, c.rolloutPercentage) "
            + "from FlagConfiguration c where c.projectId = :project and c.environment.id = :environment "
            + "and c.featureFlag.key = :key")
    Optional<EvaluationConfiguration> findForEvaluation(@Param("project") Long projectId,
            @Param("environment") Long environmentId, @Param("key") String flagKey);
    @Query("select new com.releasepilot.cache.CacheAddress(c.environment.id, c.featureFlag.key) "
            + "from FlagConfiguration c where c.projectId = :project")
    List<CacheAddress> findCacheAddressesForProject(@Param("project") Long projectId);
    @Query("select new com.releasepilot.cache.CacheAddress(c.environment.id, c.featureFlag.key) "
            + "from FlagConfiguration c where c.featureFlag.id = :flag")
    List<CacheAddress> findCacheAddressesForFlag(@Param("flag") Long flagId);
    // Bulk JPQL bypasses managed entities; flush pending writes before detaching stale references.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FlagConfiguration c where c.featureFlag.id = :flag")
    int deleteForFlag(@Param("flag") Long flag);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FlagConfiguration c where c.projectId = :project")
    int deleteForProject(@Param("project") Long project);
}
