package com.releasepilot.project.service;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.repository.ProjectRepository;
import com.releasepilot.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccess {
    private final ProjectRepository projects;
    public ProjectAccess(ProjectRepository projects) { this.projects = projects; }
    public Project requireOwned(Long actor, Long projectId) {
        return projects.findByIdAndOwner_Id(projectId, actor).orElseThrow(ProjectAccess::notFound);
    }
    public Project lockOwned(Long actor, Long projectId) {
        return projects.lockOwned(projectId, actor).orElseThrow(ProjectAccess::notFound);
    }
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
