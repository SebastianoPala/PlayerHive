package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsDTO {
    @Id
    private String id;
    private String username;
    private String pfpURL;
    private float totalHours;
    private int numGames;
    private float avgHoursPerGame;
}
