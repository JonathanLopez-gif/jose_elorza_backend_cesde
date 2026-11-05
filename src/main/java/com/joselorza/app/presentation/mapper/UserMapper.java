package com.joselorza.app.presentation.mapper;

import com.joselorza.app.domain.model.User;
import com.joselorza.app.presentation.dto.response.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return toResponse(user, null);
    }

    public UserResponse toResponse(User user, Long appointmentsCount) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getTimezone(), user.getPhone(), user.isNotifyEmail(),
                user.isActive(), user.getTermsAcceptedAt(), user.getCreatedAt(),
                appointmentsCount
        );
    }
}
