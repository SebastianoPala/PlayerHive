package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ActiveGamerDTO {

    @Id
    private String id;
    private String username;
    private String pfpURL;

    private Integer numGames;
    private LocalDateTime registrationDate;
}
