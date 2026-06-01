package com.unipi.PlayerHive.DTO.users;

import com.unipi.PlayerHive.DTO.users.friends.FriendRequestMongoDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OwnProfileMongoDTO {


    private String username;

    private String role;

    private String email;

    private String pfpURL;

    private int numGames;

    private float hoursPlayed;

    private LocalDate birthdate;

    private LocalDateTime registrationDate;

    private Integer friends;

    @Field("friendRequests")
    private List<FriendRequestMongoDTO> friendRequestsMongo;

    private Integer requestsNum;

}
