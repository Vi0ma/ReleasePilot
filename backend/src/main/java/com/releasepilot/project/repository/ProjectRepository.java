package com.releasepilot.project.repository;
import com.releasepilot.project.entity.Project;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwner_IdOrderByIdAsc(Long ownerId);
    Optional<Project> findByIdAndOwner_Id(Long id, Long ownerId);
    boolean existsByIdAndOwner_Id(Long id, Long ownerId);
    boolean existsByOwner_IdAndKey(Long ownerId, String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id and p.owner.id = :owner")
    Optional<Project> lockOwned(@Param("id") Long id, @Param("owner") Long owner);
}
