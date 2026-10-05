package com.smartmed.service;

import com.smartmed.dto.request.LoginRequest;
import com.smartmed.dto.request.RegisterRequest;
import com.smartmed.dto.response.AuthResponse;
import com.smartmed.dto.response.UserResponse;
import com.smartmed.entity.User;
import com.smartmed.exception.EmailAlreadyExistsException;
import com.smartmed.exception.InvalidCredentialsException;
import com.smartmed.repository.UserRepository;
import com.smartmed.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        long expiresIn = jwtService.getExpirationMs();
        return AuthResponse.of(token, expiresIn, UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
