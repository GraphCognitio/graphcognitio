package io.github.eduardopaim.graphcognitio.posts.mapper;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.entity.UserRole;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "authorName", source = "authorName")
    @Mapping(target = "authorUsername", source = "authorUsername")
    @Mapping(target = "authorRole", source = "authorRole")
    @Mapping(target = "likedByMe", source = "likedByMe")
    @Mapping(target = "topicKeywords", source = "topicKeywords")
    @Mapping(target = "relevanceScore", source = "relevanceScore")
    @Mapping(target = "relevanceSignals", source = "relevanceSignals")
    PostResponse toResponse(PostEntity post,
            String authorName,
            String authorUsername,
            UserRole authorRole,
            boolean likedByMe,
            List<String> topicKeywords,
            Double relevanceScore,
            List<String> relevanceSignals);

    default PostResponse toResponse(PostEntity post,
            String authorName,
            String authorUsername,
            UserRole authorRole,
            boolean likedByMe,
            List<String> topicKeywords) {
        return toResponse(post, authorName, authorUsername, authorRole, likedByMe, topicKeywords, null, List.of());
    }

    default PostResponse toResponse(PostEntity post, String authorName, String authorUsername, UserRole authorRole,
            boolean likedByMe) {
        return toResponse(post, authorName, authorUsername, authorRole, likedByMe, List.of(), null, List.of());
    }
}
