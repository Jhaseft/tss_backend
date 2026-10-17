package com.jhaseft.tss.service;

import com.jhaseft.tss.dto.CreateUserRequest;
import com.jhaseft.tss.dto.UpdateUserRequest;
import com.jhaseft.tss.model.Authority;
import com.jhaseft.tss.model.User;
import com.jhaseft.tss.repository.AuthorityRepository;
import com.jhaseft.tss.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, AuthorityRepository authorityRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public Optional<User> getById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User create(CreateUserRequest req) {
        User user = new User();
        user.setId(userRepository.findMaxId() + 1);
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setActivated(true);
        user.setCreatedBy("admin");
        user.setCreatedDate(LocalDateTime.now());
        user.setAuthorities(resolveRoles(req.roles()));
        return userRepository.save(user);
    }

    @Transactional
    public Optional<User> update(Long id, UpdateUserRequest req) {
        return userRepository.findById(id).map(user -> {
            user.setEmail(req.email());
            user.setFirstName(req.firstName());
            user.setLastName(req.lastName());
            user.setActivated(req.activated());
            user.setLastModifiedDate(LocalDateTime.now());
            if (req.password() != null && !req.password().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(req.password()));
            }
            user.setAuthorities(resolveRoles(req.roles()));
            return userRepository.save(user);
        });
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private Set<Authority> resolveRoles(Set<String> roles) {
        return roles.stream()
                .map(name -> authorityRepository.findById(name)
                        .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + name)))
                .collect(Collectors.toSet());
    }
}
