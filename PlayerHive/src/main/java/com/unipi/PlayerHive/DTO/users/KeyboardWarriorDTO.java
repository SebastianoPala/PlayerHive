package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class KeyboardWarriorDTO {

    @Id
    private String id;
    private String username;
    private String pfpURL;

    private Integer numGames;
    private Integer numReviews;

    private Double warriorRatio;

}
