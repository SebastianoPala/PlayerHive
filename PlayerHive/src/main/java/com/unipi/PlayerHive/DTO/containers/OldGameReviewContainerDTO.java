package com.unipi.PlayerHive.DTO.containers;

import com.unipi.PlayerHive.DTO.reviews.OldGameReviewDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OldGameReviewContainerDTO {
    private List<OldGameReviewDTO> reviews;
}
