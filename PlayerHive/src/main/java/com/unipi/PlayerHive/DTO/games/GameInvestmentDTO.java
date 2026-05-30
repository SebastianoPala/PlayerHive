package com.unipi.PlayerHive.DTO.games;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GameInvestmentDTO {
    @Id
    private String id;

    private String name;

    private Double price;
    private Integer discount;

    private Double finalPrice;

    //private Double avgRating;

    private Double avgTimePlayed;
    private Integer numPlayers;

    @Field("image")
    private String imageURL;

    public List<String> genres;
}
