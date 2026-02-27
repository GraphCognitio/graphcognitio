package io.github.eduardopaim.graphcognitio.admin.mapper;

import io.github.eduardopaim.graphcognitio.admin.dto.AdminUserResponse;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    AdminUserResponse toResponse(UserEntity user);
}
