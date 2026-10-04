package com.releasepilot.audit.dto;
import java.util.List;

public record AuditPage(List<AuditResponse> content, int page, int size, long totalElements) { }
