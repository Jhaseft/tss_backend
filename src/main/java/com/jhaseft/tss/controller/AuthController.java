package com.jhaseft.tss.controller;

import com.jhaseft.tss.dto.AuthResponse;
import com.jhaseft.tss.dto.LoginRequest;
import com.jhaseft.tss.model.Authority;
import com.jhaseft.tss.repository.UserRepository;
import com.jhaseft.tss.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return userRepository.findByEmail(request.email())
                .filter(u -> u.isActivated())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .map(u -> {
                    Set<String> roles = u.getAuthorities().stream()
                            .map(Authority::getName)
                            .collect(Collectors.toSet());
                    String token = jwtUtil.generate(u.getLogin(), roles);
                    return ResponseEntity.ok(new AuthResponse(token, u.getLogin(), roles));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
