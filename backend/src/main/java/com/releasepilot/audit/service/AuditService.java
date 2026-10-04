package com.releasepilot.audit.service;
import com.releasepilot.audit.dto.*;
import com.releasepilot.audit.entity.*;
import com.releasepilot.audit.repository.AuditEventRepository;
import com.releasepilot.project.repository.ProjectRepository;
import com.releasepilot.project.service.ProjectAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final ProjectRepository projects;
    public AuditService(AuditEventRepository events, ProjectRepository projects) {
        this.events = events; this.projects = projects;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actor, Long owner, Long project, AuditAction action,
                       String type, Long entityId, Map<String, Object> metadata) {
        events.save(new AuditEvent(actor, owner, project, action, type, entityId, metadata));
    }
    @Transactional(readOnly = true)
    public AuditPage list(Long actor, Long projectId, int page, int size) {
        if (!projects.existsByIdAndOwner_Id(projectId, actor)
                && !events.existsByProjectIdAndOwnerUserId(projectId, actor)) {
            throw ProjectAccess.notFound();
        }
        var result = events.findAllByProjectIdAndOwnerUserId(projectId, actor,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AuditPage(result.getContent().stream().map(AuditResponse::from).toList(),
                page, size, result.getTotalElements());
    }
    public static Map<String, Object> snapshot(String key, String name, String description) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("key", key); values.put("name", name); values.put("description", description);
        return values;
    }
}
