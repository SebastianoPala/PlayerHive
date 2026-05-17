package com.unipi.PlayerHive.utility.map;

import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import com.unipi.PlayerHive.model.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    GameReviewDTO reviewToRecentReviewDTO(Review review);
}
