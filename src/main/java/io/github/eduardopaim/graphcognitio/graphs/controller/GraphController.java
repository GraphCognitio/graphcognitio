package io.github.eduardopaim.graphcognitio.graphs.controller;

import io.github.eduardopaim.graphcognitio.graphs.GraphMode;
import io.github.eduardopaim.graphcognitio.graphs.dto.ConversationGraphResponse;
import io.github.eduardopaim.graphcognitio.graphs.service.GraphService;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/graphs")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/conversation/{rootId}")
    public ResponseEntity<ConversationGraphResponse> getConversationGraph(@PathVariable UUID rootId,
                                                                          @RequestParam(defaultValue = "2") @Min(0) int depth,
                                                                          @RequestParam(defaultValue = "120") @Min(1) int limit,
                                                                          @RequestParam(defaultValue = "conversation") String mode) {
        return ResponseEntity.ok(graphService.getConversationGraph(rootId, depth, limit, GraphMode.fromQueryParam(mode)));
    }
}
