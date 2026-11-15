package com.unireserva.back.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.unireserva.back.dto.LoginRequest;
import com.unireserva.back.dto.LoginResponse;
import com.unireserva.back.dto.LoginResult;
import com.unireserva.back.dto.SessionResponse;
import com.unireserva.back.dto.RegistrationRequest;
import com.unireserva.back.dto.RegistrationResponse;
import com.unireserva.back.model.Auth;
import com.unireserva.back.model.AuthSession;
import com.unireserva.back.model.Roles;
import com.unireserva.back.model.UserStates;
import com.unireserva.back.model.Users;
import com.unireserva.back.model.UsersRoles;
import com.unireserva.back.repository.AuthRepository;
import com.unireserva.back.repository.RolesRepository;
import com.unireserva.back.repository.UserRolesRepository;
import com.unireserva.back.repository.UsersRepository;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
@Transactional
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UsersRepository usersRepository;
    private final AuthRepository authRepository;
    private final RolesRepository rolesRepository;
    private final UserRolesRepository userRolesRepository;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            UsersRepository usersRepository,
            AuthRepository authRepository,
            RolesRepository rolesRepository,
            UserRolesRepository userRolesRepository,
            JwtService jwtService,
            AuthSessionService authSessionService,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.usersRepository = usersRepository;
        this.authRepository = authRepository;
        this.rolesRepository = rolesRepository;
        this.userRolesRepository = userRolesRepository;
        this.jwtService = jwtService;
        this.authSessionService = authSessionService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResult login(LoginRequest loginRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Correo electrónico o contraseña incorrectos");
        }

        if (!(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new IllegalStateException("La autenticación no devolvió un usuario válido");
        }

        Users user = usersRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Las credenciales no están asociadas a un usuario"));

        AuthSession session = authSessionService.createSession(user.getId(), loginRequest.rememberMe());
        String token = jwtService.generateToken(userDetails, session);
        LoginResponse response = new LoginResponse(
                user.getEmail(),
                user.getNames(),
                user.getNickname(),
                user.getUsersRoles().stream()
                        .map(usersRole -> usersRole.getRole().getName())
                        .toList()
        );

        return new LoginResult(response, token);
    }

    public void logout(Jwt jwt) {
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId == null) {
            throw new IllegalStateException("El token no contiene un identificador de sesión");
        }
        authSessionService.revokeSession(UUID.fromString(sessionId));
    }

    @Transactional(readOnly = true)
    public SessionResponse getCurrentSession(String email) {
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "La sesión ya no es válida"));

        return new SessionResponse(
                user.getEmail(),
                user.getNames(),
                user.getNickname(),
                user.getUsersRoles().stream()
                        .map(usersRole -> usersRole.getRole().getName())
                        .toList()
        );
    }

    public ResponseEntity<RegistrationResponse> register(
            RegistrationRequest registrationRequest) {

        String nickname = registrationRequest.nickname();
        if (nickname != null && nickname.isBlank()) {
            nickname = null;
        }

        if (usersRepository.existsByEmail(registrationRequest.email())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una cuenta registrada con ese correo electrónico");
        } else if (usersRepository.existsByIdNumber(registrationRequest.idNumber())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una cuenta registrada con ese número de documento");
        } else if (nickname != null && usersRepository.existsByNickname(nickname)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ese nombre de usuario ya está en uso");
        } 

        Roles studentRole = rolesRepository.findByNameIgnoreCase("student")
                .orElseThrow(() -> new IllegalStateException(
                        "El rol student no está configurado en la base de datos"));

        Users user = Users.builder()
                .documentType(registrationRequest.documentType())
                .idNumber(registrationRequest.idNumber())
                .names(registrationRequest.names())
                .surNames(registrationRequest.surNames())
                .nickname(nickname)
                .email(registrationRequest.email())
                .state(UserStates.ACTIVE)
                .creationDate(LocalDateTime.now())
                .build();
        user = usersRepository.save(user);

        userRolesRepository.save(UsersRoles.builder()
                .user(user)
                .role(studentRole)
                .build());

        authRepository.save(Auth.builder()
                .user(user)
                .supplier("LOCAL")
                .passHash(passwordEncoder.encode(registrationRequest.password()))
                .build());

        RegistrationResponse response = new RegistrationResponse(
                "Usuario registrado correctamente",
                user.getEmail(),
                user.getNames(),
                user.getNickname()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
