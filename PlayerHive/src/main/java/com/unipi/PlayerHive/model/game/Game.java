package com.unipi.PlayerHive.model.game;

import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Document(collection = "games")
public class Game {
    @Id
    private String id;

    private String name;
    @Field("release_date")
    private LocalDate releaseDate;
    private Double price;
    private Integer discount;
    private Double finalPrice;

    private String description;

    private List<GameReviewDTO> recentReviews;
    private List<ObjectId> allReviews;

    @Field("image")
    private String imageURL;
    private List<String> supportedOS;
    private Integer achievements;
    private Float sumScore;
    private Integer countScore;
    private Float totalHoursPlayed;
    private Integer numPlayers;
    private List<String> developers;
    private List<String> publishers;
    private List<String> genres;

}
