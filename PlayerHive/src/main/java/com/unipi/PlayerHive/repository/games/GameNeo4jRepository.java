package com.unipi.PlayerHive.repository.games;

import com.unipi.PlayerHive.DTO.games.HiddenGemDTO;
import com.unipi.PlayerHive.DTO.games.PlaytimeAchievementsDTO;
import com.unipi.PlayerHive.DTO.games.RelatedGameDTO;
import com.unipi.PlayerHive.DTO.games.GameRecommendationDTO;
import com.unipi.PlayerHive.DTO.games.TrendingGameDTO;
import com.unipi.PlayerHive.DTO.users.GameOwnerDTO;
import com.unipi.PlayerHive.model.game.GameNeo4j;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

/**
 * Repository interface for managing Game entities and their relationships in Neo4j.
 */
@Repository
public interface GameNeo4jRepository extends Neo4jRepository<GameNeo4j,String>{

    /**
     * Deletes PLAYED relationships targeting a specific game in batches to avoid overwhelming the database.
     * @param gameId The ID of the game.
     * @param batchSize The limit of items to process in one batch.
     * @return A list of removed relationships mapping users to their lost playtime.
     */
    @Query("MATCH (u:User)-[r:PLAYED]->(g:Game {id: $gameId}) " +
            "WITH u, r LIMIT $batchSize " +
            "WITH u.id AS id, r.hoursPlayed AS hoursPlayed, r " +
            "DELETE r " +
            "RETURN id, hoursPlayed")
    List<GameOwnerDTO> deletePlayedEdgesInBatch(String gameId, int batchSize);

    /**
     * Retrieves a user's playtime for a game along with the game's (NOT the user's) total possible achievements.
     * @param userId The ID of the user.
     * @param gameId The ID of the game.
     * @return A DTO containing the playtime and game achievements.
     */
    @Query("MATCH (g:Game {id: $gameId}) " +
            "OPTIONAL MATCH (u:User {id: $userId})-[r:PLAYED]->(g) " +
            "RETURN r.hoursPlayed as hoursPlayed, g.achievements as achievements")
    PlaytimeAchievementsDTO findUserPlaytimeAndGameAchievements(String userId, String gameId);

    // INTERESTING QUERIES ========================================

    /**
     * Recommends games frequently played by the user's friends but not by the user yet.
     * @param userId The ID of the user.
     * @param limit Maximum number of game recommendations to return.
     * @return A list of GameRecommendationDTO.
     */
    @Query("MATCH (u:User {id: $userId})-[:FRIENDS_WITH]->(friend:User)-[:PLAYED]->(recGame:Game) " +
            "WHERE NOT (u)-[:PLAYED]->(recGame) " +
            "RETURN recGame.id as gameId, recGame.name AS name, recGame.image as image, " +
            " count(friend) AS friendsWhoPlay " +
            "ORDER BY friendsWhoPlay DESC " +
            "LIMIT $limit")
    List<GameRecommendationDTO> getGameRecommendations(String userId, int limit);

    /**
     * Retrieves games that are currently trending among interconnected friend groups.
     * @param limit Maximum number of games to return.
     * @param minSocialCount Minimum popularity threshold within friend groups.
     * @return A list of TrendingGameDTO.
     */
    @Query("MATCH (u1:User)-[:FRIENDS_WITH]->(u2:User) " +
            "WHERE elementId(u1) < elementId(u2) " +
            "MATCH (u1)-[:PLAYED]->(g:Game)<-[:PLAYED]-(u2) " +
            "WITH g.name AS name, count(*) AS socialPlayCount " +
            "WHERE socialPlayCount > $minSocialCount " +
            "RETURN name, socialPlayCount " +
            "ORDER BY socialPlayCount DESC " +
            "LIMIT $limit")
    List<TrendingGameDTO> getTrendingGamesAmongFriends(int limit, int minSocialCount);

    /**
     * Finds "Hidden Gems" that are played by friends but have a low global popularity.
     * @param userId The ID of the user.
     * @param nicheThreshold Maximum threshold for global popularity to be considered a niche game.
     * @return A list of HiddenGemDTO.
     */
    @Query("MATCH (u:User {id: $userId})-[:FRIENDS_WITH]-(friend)-[:PLAYED]->(game:Game) " +
            "WHERE NOT (u)-[:PLAYED]->(game) " +
            "WITH game, count(DISTINCT friend) AS friendsPlaying " +
            "WITH game, friendsPlaying " +
            "ORDER BY friendsPlaying DESC " +
            "LIMIT 50 " +
            "MATCH (game)<-[:PLAYED]-(globalPlayer) " +
            "WITH game, friendsPlaying, count(globalPlayer) AS globalPopularity " +
            "WHERE globalPopularity < $nicheThreshold " +
            "RETURN game.id as gameId, game.name AS name, game.image as image, friendsPlaying AS friendsPlaying, globalPopularity AS globalPopularity " +
            "ORDER BY friendsPlaying DESC, globalPopularity ASC " +
            "LIMIT 10")
    List<HiddenGemDTO> getHiddenGems(String userId, int nicheThreshold);

    /**
     * Recommends games commonly played by users who also play the specified game.
     * @param gameId The source game ID.
     * @param minShared Minimum number of shared players required to form a relation.
     * @param limit Maximum number of related games to return.
     * @return A list of RelatedGameDTO.
     */
    @Query("MATCH (g:Game {id: $gameId})<-[:PLAYED]-(u:User)-[:PLAYED]->(other:Game) " +
            "WHERE g <> other " +
            "WITH other, count(DISTINCT u) AS sharedPlayers " +
            "WHERE sharedPlayers >= $minShared " +
            "RETURN other.id AS gameId, other.name AS name, other.image AS image, sharedPlayers " +
            "ORDER BY sharedPlayers DESC " +
            "LIMIT $limit")
    List<RelatedGameDTO> getRelatedGames(String gameId, int minShared, int limit);

}