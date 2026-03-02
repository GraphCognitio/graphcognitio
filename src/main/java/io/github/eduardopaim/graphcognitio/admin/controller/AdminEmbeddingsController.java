package io.github.eduardopaim.graphcognitio.admin.controller;

import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexHistoryEntry;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingReindexResponse;
import io.github.eduardopaim.graphcognitio.admin.dto.EmbeddingsStatusResponse;
import io.github.eduardopaim.graphcognitio.admin.service.AdminService;
import io.github.eduardopaim.graphcognitio.exception.UnauthorizedException;
import io.github.eduardopaim.graphcognitio.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/admin/embeddings")
public class AdminEmbeddingsController {

    private final AdminService adminService;

    public AdminEmbeddingsController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    public ResponseEntity<EmbeddingsStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return ResponseEntity.ok(adminService.embeddingsStatus(authenticatedUser.userId()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<EmbeddingReindexHistoryEntry>> history(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                                     @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return ResponseEntity.ok(adminService.embeddingsHistory(authenticatedUser.userId(), limit));
    }

    @PostMapping("/reindex")
    public ResponseEntity<EmbeddingReindexResponse> reindex(@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
                                                            @RequestParam(defaultValue = "missing") String scope,
                                                            @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit) {
        if (authenticatedUser == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return ResponseEntity.ok(adminService.reindexEmbeddings(authenticatedUser.userId(), scope, limit));
    }
}
