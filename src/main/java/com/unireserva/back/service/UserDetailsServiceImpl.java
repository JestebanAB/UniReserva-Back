package com.unireserva.back.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.unireserva.back.model.Auth;
import com.unireserva.back.model.UserStates;
import com.unireserva.back.model.UsersRoles;
import com.unireserva.back.model.Roles;
import com.unireserva.back.repository.AuthRepository;

import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class UserDetailsServiceImpl implements UserDetailsService {
    private final AuthRepository authRepository;

    public UserDetailsServiceImpl(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Auth auth = authRepository.findPasswordAuthByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No se encontraron credenciales para el correo proporcionado"));

        var authorities = auth.getUser().getUsersRoles().stream()
                .map(UsersRoles::getRole)
                .filter(Objects::nonNull)
                .map(Roles::getName)
                .filter(Objects::nonNull)
                .map(name -> "ROLE_" + name.toUpperCase(Locale.ROOT))
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UserStates state = auth.getUser().getState();
        boolean accountEnabled = UserStates.ACTIVE == state || UserStates.INACTIVE == state;

        return User.withUsername(auth.getUser().getEmail())
                .password(auth.getPassHash())
                .authorities(authorities)
                .disabled(!accountEnabled)
                .build();
    }
}
