package com.releasepilot.project.service;
import com.releasepilot.audit.entity.AuditAction;
import com.releasepilot.audit.service.AuditService;
import com.releasepilot.apikey.service.ApiKeyService;
import com.releasepilot.environment.service.EnvironmentService;
import com.releasepilot.featureflag.service.FeatureFlagService;
import com.releasepilot.project.dto.*;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.repository.ProjectRepository;
import com.releasepilot.shared.error.ApiException;
import com.releasepilot.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserRepository users;
    private final EnvironmentService environments;
    private final FeatureFlagService flags;
    private final ProjectAccess access;
    private final AuditService audit;
    private final ApiKeyService apiKeys;
    public ProjectService(ProjectRepository projects, UserRepository users, EnvironmentService environments,
                          FeatureFlagService flags, ProjectAccess access, AuditService audit, ApiKeyService apiKeys) {
        this.projects = projects; this.users = users; this.environments = environments;
        this.flags = flags; this.access = access; this.audit = audit;
        this.apiKeys = apiKeys;
    }
    @Transactional
    public ProjectResponse create(Long actor, CreateProjectRequest request) {
        if (projects.existsByOwner_IdAndKey(actor, request.key())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PROJECT_KEY", "Project key already exists");
        }
        var project = projects.saveAndFlush(new Project(request.name(), request.key(), request.description(),
                users.getReferenceById(actor)));
        environments.createDefaults(project);
        audit.record(actor, actor, project.getId(), AuditAction.PROJECT_CREATED, "PROJECT", project.getId(),
                Map.of("resource", snapshot(project), "environments", List.of("development", "staging", "production")));
        return ProjectResponse.from(project);
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long actor) {
        return projects.findAllByOwner_IdOrderByIdAsc(actor).stream().map(ProjectResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public ProjectResponse get(Long actor, Long projectId) {
        return ProjectResponse.from(access.requireOwned(actor, projectId));
    }
    @Transactional
    public ProjectResponse update(Long actor, Long projectId, UpdateProjectRequest request) {
        var project = access.lockOwned(actor, projectId);
        var before = snapshot(project);
        project.update(request.name(), request.description());
        projects.flush();
        audit.record(actor, actor, projectId, AuditAction.PROJECT_UPDATED, "PROJECT", projectId,
                Map.of("before", before, "after", snapshot(project)));
        return ProjectResponse.from(project);
    }
    @Transactional
    public void delete(Long actor, Long projectId) {
        var project = access.lockOwned(actor, projectId);
        var before = snapshot(project);
        int flagCount = flags.deleteForProject(project);
        int apiKeyCount = apiKeys.deleteForProject(project);
        int environmentCount = environments.deleteForProject(project);
        projects.delete(project);
        projects.flush();
        audit.record(actor, actor, projectId, AuditAction.PROJECT_DELETED, "PROJECT", projectId,
                Map.of("resource", before, "deletedFlags", flagCount, "deletedEnvironments", environmentCount,
                        "deletedApiKeys", apiKeyCount));
    }
    private Map<String, Object> snapshot(Project project) {
        return AuditService.snapshot(project.getKey(), project.getName(), project.getDescription());
    }
}
