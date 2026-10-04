package com.releasepilot.project.controller;
import com.releasepilot.project.dto.*;
import com.releasepilot.project.service.ProjectService;
import java.util.List;
import com.releasepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody CreateProjectRequest request) {
        return projects.create(actor.id(), request);
    }
    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal UserPrincipal actor) { return projects.list(actor.id()); }
    @GetMapping("/{projectId}")
    public ProjectResponse get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId) {
        return projects.get(actor.id(), projectId);
    }
    @PutMapping("/{projectId}")
    public ProjectResponse update(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update(actor.id(), projectId, request);
    }
    @DeleteMapping("/{projectId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long projectId) {
        projects.delete(actor.id(), projectId);
    }
}
