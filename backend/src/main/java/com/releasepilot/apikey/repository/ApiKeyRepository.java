package com.releasepilot.apikey.repository;

import com.releasepilot.apikey.entity.ApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findAllByEnvironment_IdOrderByIdAsc(Long environmentId);
    Optional<ApiKey> findByIdAndEnvironment_Id(Long id, Long environmentId);
    @Query("select k from ApiKey k join fetch k.environment e join fetch e.project where k.publicId = :publicId")
    Optional<ApiKey> findForAuthentication(@Param("publicId") String publicId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ApiKey k where k.environment.id in (select e.id from Environment e where e.project.id = :project)")
    int deleteForProject(@Param("project") Long projectId);
}
