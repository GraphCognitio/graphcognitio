package io.github.eduardopaim.graphcognitio.graphs.dto;

import java.util.List;

public record ConversationGraphResponse(
        List<ConversationNodeResponse> nodes,
        List<ConversationEdgeResponse> edges
) {
}
