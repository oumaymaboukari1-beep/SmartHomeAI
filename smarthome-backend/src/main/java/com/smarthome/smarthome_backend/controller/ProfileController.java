package com.smarthome.smarthome_backend.controller;

import com.smarthome.smarthome_backend.repository.UserRepository;
import com.smarthome.smarthome_backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final UserRepository users;
    private final JwtService jwtService;

    public ProfileController(UserRepository users, JwtService jwtService) {
        this.users = users;
        this.jwtService = jwtService;
    }

    @GetMapping
    public AuthController.UserSummary get(Authentication authentication) {
        return summary(authentication.getName());
    }

    @PutMapping
    @Transactional
    public AuthController.AuthResponse update(@Valid @RequestBody ProfileRequest request,
                                              Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered");
        }
        user.setFirstname(request.firstname().trim());
        user.setLastname(request.lastname().trim());
        user.setEmail(email);
        users.save(user);
        var summary = new AuthController.UserSummary(user.getId(), user.getFirstname(), user.getLastname(),
                user.getEmail(), user.getRole());
        return new AuthController.AuthResponse(jwtService.issue(user.getEmail(), user.getRole()), summary);
    }

    private AuthController.UserSummary summary(String email) {
        var user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new AuthController.UserSummary(user.getId(), user.getFirstname(), user.getLastname(),
                user.getEmail(), user.getRole());
    }

    public record ProfileRequest(@NotBlank @Size(max = 80) String firstname,
                                 @NotBlank @Size(max = 80) String lastname,
                                 @NotBlank @Email @Size(max = 254) String email) {}
}
