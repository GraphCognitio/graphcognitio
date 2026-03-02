package io.github.eduardopaim.graphcognitio.users;

import io.github.eduardopaim.graphcognitio.users.dto.UserProfileResponse;
import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import io.github.eduardopaim.graphcognitio.repository.UserRepository;
import io.github.eduardopaim.graphcognitio.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Approximate posts count. For better performance we can keep a counter or do a
        // fast query.
        // Placeholder, we can add a proper count query later or just return 0.
        return new UserProfileResponse(
                user.getUsername(),
                user.getName(),
                user.getBio(),
                user.getCreatedAt(),
                user.getAvatarUrl(),
                0);
    }

    @Transactional
    public UserProfileResponse updateProfile(java.util.UUID userId,
            io.github.eduardopaim.graphcognitio.users.dto.UpdateProfileRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.name() != null && !request.name().trim().isEmpty()) {
            user.setName(request.name().trim());
        }

        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }

        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }

        userRepository.save(user);

        return new UserProfileResponse(
                user.getUsername(),
                user.getName(),
                user.getBio(),
                user.getCreatedAt(),
                user.getAvatarUrl(),
                0);
    }
}
