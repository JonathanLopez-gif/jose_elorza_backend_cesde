package com.joselorza.app.application.service;

import com.joselorza.app.domain.exception.BusinessRuleException;
import com.joselorza.app.domain.exception.ResourceAlreadyExistsException;
import com.joselorza.app.domain.exception.ResourceNotFoundException;
import com.joselorza.app.domain.model.enumerated.Role;
import com.joselorza.app.infrastructure.repository.AppointmentRepository;
import com.joselorza.app.infrastructure.repository.RefreshTokenRepository;
import com.joselorza.app.infrastructure.repository.UserRepository;
import com.joselorza.app.domain.model.User;
import com.joselorza.app.presentation.dto.request.AdminCreateUserRequest;
import com.joselorza.app.presentation.dto.request.AdminUpdateUserRequest;
import com.joselorza.app.presentation.dto.request.UpdateUserRequest;
import com.joselorza.app.presentation.dto.response.UserResponse;
import com.joselorza.app.presentation.mapper.UserMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // ---------------------------------------------------------------
    // Perfil propio
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Administración (ADMIN)
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return listUsers(null, null, null);
    }

    /**
     * Listado con filtros opcionales: texto (nombre o email), rol y estado.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(String search, Role role, Boolean active) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Una sola consulta agregada para el número de citas de cada usuario
        Map<UUID, Long> appointmentsByUser = new HashMap<>();
        for (Object[] row : appointmentRepository.countGroupedByUser()) {
            appointmentsByUser.put((UUID) row[0], (Long) row[1]);
        }

        return userRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(user -> userMapper.toResponse(user, appointmentsByUser.getOrDefault(user.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return userMapper.toResponse(findUserOrThrow(userId));
    }

    public UserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyExistsException();
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : Role.USER)
                .timezone(request.timezone())
                .phone(request.phone())
                .notifyEmail(true)
                .isActive(true)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(UUID requesterId, UUID userId, AdminUpdateUserRequest request) {
        User user = findUserOrThrow(userId);

        if (requesterId.equals(userId) && request.role() != null && request.role() != user.getRole()) {
            throw new BusinessRuleException("You can't change your own role");
        }

        user.setName(request.name());
        user.setTimezone(request.timezone());
        user.setPhone(request.phone());
        user.setNotifyEmail(request.notifyEmail());
        if (request.role() != null) {
            user.setRole(request.role());
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    public void activateUser(UUID userId) {
        User user = findUserOrThrow(userId);
        user.setActive(true);
        userRepository.save(user);
    }

    public void deactivateUser(UUID requesterId, UUID userId) {
        if (requesterId.equals(userId)) {
            throw new BusinessRuleException("You can't deactivate your own account");
        }
        User user = findUserOrThrow(userId);
        user.setActive(false);
        userRepository.save(user);
        // Un usuario desactivado no puede renovar su sesión
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException());
    }
}
