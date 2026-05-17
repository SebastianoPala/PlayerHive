package com.unipi.PlayerHive.DTO.containers;

import com.unipi.PlayerHive.DTO.users.friends.FriendRequestMongoDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FriendRequestMongoContainerDTO {
    public List<FriendRequestMongoDTO> friendRequests;
}
