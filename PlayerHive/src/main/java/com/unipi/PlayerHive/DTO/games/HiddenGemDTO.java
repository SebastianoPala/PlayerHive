package com.unipi.PlayerHive.DTO.games;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HiddenGemDTO {

    private String gameId;
    private String name;
    private String image;
    private int friendsPlaying;
    private int globalPopularity;

}
