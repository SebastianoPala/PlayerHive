package com.unipi.PlayerHive.DTO.containers;

import com.unipi.PlayerHive.DTO.reviews.OldUserReviewDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OldUserReviewContainerDTO {
    private List<OldUserReviewDTO> reviews;
    private Integer reviewsNum;
}
