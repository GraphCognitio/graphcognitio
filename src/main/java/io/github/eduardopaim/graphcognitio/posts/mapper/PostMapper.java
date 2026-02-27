package io.github.eduardopaim.graphcognitio.posts.mapper;

import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.posts.dto.PostResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "authorName", source = "authorName")
    @Mapping(target = "likedByMe", source = "likedByMe")
    PostResponse toResponse(PostEntity post, String authorName, boolean likedByMe);
}
