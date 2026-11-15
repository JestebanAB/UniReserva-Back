package com.unireserva.back.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.unireserva.back.config.AuthCookieService;
import com.unireserva.back.dto.CsrfTokenResponse;
import com.unireserva.back.dto.LoginRequest;
import com.unireserva.back.dto.LoginResponse;
import com.unireserva.back.dto.SessionResponse;
import com.unireserva.back.dto.RegistrationRequest;
import com.unireserva.back.dto.RegistrationResponse;
import com.unireserva.back.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        var loginResult = authService.login(loginRequest);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.createAuthCookie(
                        loginResult.token(), loginRequest.rememberMe()).toString())
                .body(loginResult.response());
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(new CsrfTokenResponse(csrfToken.getToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearAuthCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<SessionResponse> currentSession(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.getCurrentSession(jwt.getSubject()));
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest registrationRequest) {
        return authService.register(registrationRequest);
    }

}