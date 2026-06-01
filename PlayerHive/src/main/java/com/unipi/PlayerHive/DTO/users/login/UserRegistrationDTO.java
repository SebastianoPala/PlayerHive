package com.unipi.PlayerHive.DTO.users.login;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import lombok.Getter;

public record UserRegistrationDTO (
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank @Email String email,
    String profile_picture,
    @Past LocalDate birthDate
) {}
