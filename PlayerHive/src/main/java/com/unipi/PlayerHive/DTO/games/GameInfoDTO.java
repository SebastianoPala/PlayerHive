package com.unipi.PlayerHive.DTO.games;

import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

public class GameInfoDTO {

    private String name;
    private LocalDate releaseDate;
    private Double price;
    private Integer discount;

    private Double finalPrice;

    private String description;

    private List<GameReviewDTO> recentReviews;

    private String imageURL;
    private List<String> supportedOS;
    private Integer achievements;
    private Float userScore;
    private Float averagePlaytime;
    private List<String> developers;
    private List<String> publishers;
    private List<String> genres;
}
