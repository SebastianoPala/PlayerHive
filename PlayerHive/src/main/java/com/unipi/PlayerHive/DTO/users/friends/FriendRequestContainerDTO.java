package com.unipi.PlayerHive.DTO.users.friends;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class FriendRequestContainerDTO {
    private List<FriendRequestDTO> friendRequests;

    private int requestsNum;

    private int numPages;

    private boolean isLastPage;
}
