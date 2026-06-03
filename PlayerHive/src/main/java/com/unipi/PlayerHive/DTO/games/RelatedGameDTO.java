package com.unipi.PlayerHive.DTO.games;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RelatedGameDTO {

    private String gameId;
    private String name;
    private String image;
    private Long sharedPlayers;
}
