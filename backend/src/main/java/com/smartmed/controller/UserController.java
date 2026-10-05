package com.smartmed.controller;

import com.smartmed.dto.response.ApiResponse;
import com.smartmed.dto.response.UserResponse;
import com.smartmed.security.SmartMedUserDetails;
import com.smartmed.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal SmartMedUserDetails principal) {
        return ApiResponse.ok(userService.getCurrentUser(principal));
    }
}
