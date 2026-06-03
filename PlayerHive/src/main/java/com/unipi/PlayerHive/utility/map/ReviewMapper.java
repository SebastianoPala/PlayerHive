package com.unipi.PlayerHive.utility.map;

import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import com.unipi.PlayerHive.model.Review;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper interface for converting Review-related entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface ReviewMapper {

    /**
     * Maps a Review entity to a GameReviewDTO for recent review displays.
     *
     * @param review The Review entity to map.
     * @return The resulting GameReviewDTO.
     */
    GameReviewDTO reviewToRecentReviewDTO(Review review);
}