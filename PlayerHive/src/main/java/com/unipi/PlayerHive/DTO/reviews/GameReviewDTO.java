package com.unipi.PlayerHive.DTO.reviews;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class GameReviewDTO {

    @Id
    private String id;

    @Field("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId userId;
    private String username;
    private String pfpURL;

    @Field("review_text")
    private String reviewText;

    private Float score;

    private LocalDateTime timestamp;
}
