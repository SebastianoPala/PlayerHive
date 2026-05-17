package com.unipi.PlayerHive.DTO.reviews;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter

public class OldUserReviewDTO {

    @Field("review_id")
    private ObjectId reviewId;

    @Field("game_id")
    private ObjectId gameId;

}
