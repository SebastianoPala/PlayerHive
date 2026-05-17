package com.unipi.PlayerHive.DTO.reviews;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserReviewDTO {
    @Id
    private String id;

    @Field("game_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId gameId;

    @Field("game_name")
    private String gameName;

    @Field("game_image")
    private String gameImg;

    @Field("review_text")
    private String reviewText;

    private Float score;

    private LocalDateTime timestamp;

}
