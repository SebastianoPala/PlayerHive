package com.unipi.PlayerHive.DTO.users.friends;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@AllArgsConstructor
public class FriendRecommendationDTO {

    private String userId;
    private String username;
    private String pfpURL;
    private int mutualFriendsCount;
}
