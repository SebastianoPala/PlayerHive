package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.users.login.UserLoginDTO;
import com.unipi.PlayerHive.DTO.users.login.UserRegistrationDTO;
import com.unipi.PlayerHive.config.JwtUtils;
import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.model.user.UserNeo4j;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.repository.users.UserNeo4jRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import jakarta.annotation.Nonnull;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Service class responsible for handling user registration and authentication logic.
 */
@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final PasswordEncoder encoder;

    @Autowired
    public AuthService(AuthenticationManager authManager, UserRepository userRepository, UserNeo4jRepository userNeo4jRepository, PasswordEncoder encoder){
        this.authManager = authManager;
        this.userRepository = userRepository;
        this.userNeo4jRepository = userNeo4jRepository;
        this.encoder = encoder;
    }

    /**
     * Registers a new user, saving data in both MongoDB and Neo4j.
     * Validates that the username and email are unique.
     *
     * @param dto The registration details provided by the user.
     * @throws IllegalArgumentException if the username or email already exists.
     */
    @Transactional
    public void registerUser(@Nonnull @Valid @RequestBody UserRegistrationDTO dto){

        userRepository.findLightByUsernameOrEmail(dto.username(), dto.email()).ifPresent(user -> {
            if (user.getUsername().equals(dto.username())) {
                throw new IllegalArgumentException("Username already exists");
            }
            if (user.getEmail().equals(dto.email())) {
                throw new IllegalArgumentException("Email already exists");
            }
        });

        // the userId will be obtained by mongoDB
        User newUser = new User(null, dto.username(), encoder.encode(dto.password()), "USER", dto.email(),
                                dto.profile_picture(), 0, 0f, dto.birthDate(),
                                LocalDateTime.now(), 0, new ArrayList<>(), 0, new ArrayList<>());
                                                               // friend requests               // reviews

        User savedUser = userRepository.save(newUser);

        UserNeo4j neo4jUser = new UserNeo4j(savedUser.getId(), dto.username(), dto.profile_picture());
        userNeo4jRepository.save(neo4jUser);
    }

    /**
     * Authenticates a user based on email and password.
     *
     * @param dto The login credentials.
     * @return A JWT token if authentication is successful, null otherwise.
     */
    public String loginUser(@Nonnull @Valid @RequestBody UserLoginDTO dto){
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        if(auth.isAuthenticated()){
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return JwtUtils.generateToken(principal.getUser().getId());
        }
        return null;
    }
}