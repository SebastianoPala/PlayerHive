package com.unipi.PlayerHive.DTO.containers;

import com.unipi.PlayerHive.DTO.users.UserSearchDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserSearchContainerDTO {

    private List<UserSearchDTO> searchResult;

    private boolean isLastPage;
}
