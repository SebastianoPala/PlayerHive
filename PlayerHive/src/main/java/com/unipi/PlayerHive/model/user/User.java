package com.unipi.PlayerHive.model.user;

import com.unipi.PlayerHive.DTO.reviews.OldUserReviewDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String username;
    private String password;

    private String role;

    @Email
    private String email;

    private String pfpURL;

    private int numGames;

    private float hoursPlayed;

    @Past
    private LocalDate birthdate;

    @Past
    private LocalDateTime registrationDate;

    private Integer friends;

    private List<FriendRequestDTO> friendRequests;
    private Integer requestsNum;

    private List<OldUserReviewDTO> reviewIds;

}
