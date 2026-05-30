package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OwnProfileDTO {


    private String username;

    private String role;

    private String email;

    private String pfpURL;

    private int numGames;

    private float hoursPlayed;

    private LocalDate birthdate;

    private LocalDateTime registrationDate;

    private Integer friends;

    private Integer requestsNum;

}
