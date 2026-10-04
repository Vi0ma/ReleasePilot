package com.releasepilot.environment.service;
import com.releasepilot.environment.dto.EnvironmentResponse;
import com.releasepilot.environment.entity.Environment;
import com.releasepilot.environment.repository.EnvironmentRepository;
import com.releasepilot.project.entity.Project;
import com.releasepilot.project.service.ProjectAccess;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EnvironmentService {
    private final EnvironmentRepository environments;
    private final ProjectAccess access;
    public EnvironmentService(EnvironmentRepository environments, ProjectAccess access) {
        this.environments = environments; this.access = access;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void createDefaults(Project project) {
        environments.saveAllAndFlush(List.of(
                new Environment(project, "Development", "development"),
                new Environment(project, "Staging", "staging"),
                new Environment(project, "Production", "production")));
    }
    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list(Long actor, Long projectId) {
        access.requireOwned(actor, projectId);
        return environments.findAllByProject_IdOrderByIdAsc(projectId).stream().map(EnvironmentResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public EnvironmentResponse get(Long actor, Long projectId, Long environmentId) {
        access.requireOwned(actor, projectId);
        return EnvironmentResponse.from(environments.findByIdAndProject_Id(environmentId, projectId)
                .orElseThrow(ProjectAccess::notFound));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Environment> forProject(Project ownedProject) {
        return environments.findAllByProject_IdOrderByIdAsc(ownedProject.getId());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteForProject(Project ownedProject) {
        return environments.deleteForProject(ownedProject.getId());
    }
}
