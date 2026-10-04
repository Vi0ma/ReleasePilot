package com.releasepilot.environment.repository;
import com.releasepilot.environment.entity.Environment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
    List<Environment> findAllByProject_IdOrderByIdAsc(Long projectId);
    Optional<Environment> findByIdAndProject_Id(Long id, Long projectId);
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Environment e where e.project.id = :project")
    int deleteForProject(@Param("project") Long project);
}
