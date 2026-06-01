package com.unipi.PlayerHive.controller;

import com.unipi.PlayerHive.DTO.users.login.AccessTokenDTO;
import com.unipi.PlayerHive.DTO.users.login.UserLoginDTO;
import com.unipi.PlayerHive.DTO.users.login.UserRegistrationDTO;
import com.unipi.PlayerHive.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")

@Tag(name = "2. Authentication", description = "Public endpoints for Registration and Login")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new account. Checks that username and email are not already in use.")
    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Username or Email already exists")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserRegistrationDTO dto){
        authService.registerUser(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates the user and returns a JWT Bearer token for subsequent requests.")
    @ApiResponse(responseCode = "200", description = "Login successful, Token returned")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<?> loginUser(@RequestBody UserLoginDTO dto){
        try{
            String token = authService.loginUser(dto);
            if(token != null)
                return ResponseEntity.ok(new AccessTokenDTO(token));
            else
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        } catch(AuthenticationException e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }
}
