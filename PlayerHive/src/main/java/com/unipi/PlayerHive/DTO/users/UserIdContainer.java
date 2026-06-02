package com.unipi.PlayerHive.DTO.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UserIdContainer {
    private List<ObjectId> userIds;
}
