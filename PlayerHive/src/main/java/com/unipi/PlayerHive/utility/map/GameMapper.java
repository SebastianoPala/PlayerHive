package com.unipi.PlayerHive.utility.map;

import com.unipi.PlayerHive.DTO.games.*;
import com.unipi.PlayerHive.model.game.Game;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper interface for converting Game-related entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface GameMapper {

    /**
     * Maps a Game entity to a GameInfoDTO.
     *
     * @param game The Game entity to map.
     * @return The resulting GameInfoDTO.
     */
    GameInfoDTO gameToGameInfoDTO(Game game);


    /**
     * Maps an AddGameDTO to a Game entity.
     *
     * @param addGameDTO The data transfer object containing new game details.
     * @return The resulting Game entity.
     */
    Game editGameDTOtoGame(AddGameDTO addGameDTO);

}