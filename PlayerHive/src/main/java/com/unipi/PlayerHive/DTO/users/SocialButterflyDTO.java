package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SocialButterflyDTO {
    private String username;
    private Long friendCount;
    private Long gamesPlayed;
    private Long socialScore;
}
