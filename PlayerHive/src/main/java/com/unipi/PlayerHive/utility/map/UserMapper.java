package com.unipi.PlayerHive.utility.map;

import com.unipi.PlayerHive.DTO.users.OwnProfileDTO;
import com.unipi.PlayerHive.DTO.users.OwnProfileMongoDTO;
import com.unipi.PlayerHive.DTO.users.ProfileDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestMongoDTO;
import com.unipi.PlayerHive.model.user.User;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper interface for converting User-related entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a User entity to a ProfileDTO.
     *
     * @param user The User entity to map.
     * @return The resulting ProfileDTO.
     */
    ProfileDTO userToProfileDTO(User user);

    /**
     * Maps an OwnProfileMongoDTO to an OwnProfileDTO.
     *
     * @param ownMongo The OwnProfileMongoDTO retrieved from MongoDB.
     * @return The resulting OwnProfileDTO.
     */
    OwnProfileDTO OwnProfileMongoToOwnProfileDTO(OwnProfileMongoDTO ownMongo);
}