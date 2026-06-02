package com.unipi.PlayerHive.DTO.games;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LibraryGameLightDTO {
    @Id
    private String id;
    private Double hoursPlayed;
}
