package com.unipi.PlayerHive.utility.map;

import com.unipi.PlayerHive.DTO.users.OwnProfileDTO;
import com.unipi.PlayerHive.DTO.users.OwnProfileMongoDTO;
import com.unipi.PlayerHive.DTO.users.ProfileDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestMongoDTO;
import com.unipi.PlayerHive.model.user.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    ProfileDTO userToProfileDTO(User user);

    OwnProfileDTO OwnProfileMongoToOwnProfileDTO(OwnProfileMongoDTO ownMongo);

}
