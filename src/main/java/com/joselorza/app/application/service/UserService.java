package com.joselorza.app.application.service;

import com.joselorza.app.application.exception.UserNotFoundException;
import com.joselorza.app.application.port.out.UserRepository;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.presentation.dto.request.UpdateUserRequest;
import com.joselorza.app.presentation.dto.response.UserResponse;
import com.joselorza.app.presentation.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return userMapper.toResponse(findUserOrThrow(userId));
    }

    public UserResponse updateProfile(UUID userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        user.setName(request.name());
        user.setTimezone(request.timezone());
        user.setPhone(request.phone());
        user.setNotifyEmail(request.notifyEmail());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public void deactivateUser(UUID userId) {
        User user = findUserOrThrow(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
