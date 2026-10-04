package com.releasepilot.audit.repository;
import com.releasepilot.audit.entity.AuditEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    boolean existsByProjectIdAndOwnerUserId(Long projectId, Long ownerUserId);
    Page<AuditEvent> findAllByProjectIdAndOwnerUserId(Long projectId, Long ownerUserId, Pageable pageable);
}
