package io.github.eduardopaim.graphcognitio.auth.mapper;

import io.github.eduardopaim.graphcognitio.auth.dto.AuthUserResponse;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthUserMapper {

    AuthUserResponse toResponse(UserEntity user);
}
