package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GamingTwinDTO {
    private String userId;
    private String username;
    private String pfpURL;
    private double jaccardSimilarity;
}
