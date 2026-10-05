package com.smartmed.service;

import com.smartmed.dto.response.UserResponse;
import com.smartmed.exception.ResourceNotFoundException;
import com.smartmed.repository.UserRepository;
import com.smartmed.security.SmartMedUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(SmartMedUserDetails principal) {
        return userRepository.findById(principal.getId())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
