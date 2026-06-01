package com.unipi.PlayerHive.DTO.containers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.List;

@AllArgsConstructor
@Getter
public class ReviewIdContainerDTO {
    private List<ObjectId> reviews;
    private Integer countScore;
}
