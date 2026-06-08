package com.unipi.PlayerHive.repository.users;

import com.unipi.PlayerHive.DTO.games.LibraryGameDTO;
import com.unipi.PlayerHive.DTO.games.LibraryGameLightDTO;
import com.unipi.PlayerHive.DTO.users.GamingTwinDTO;

import com.unipi.PlayerHive.DTO.users.friends.FriendDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRecommendationDTO;
import com.unipi.PlayerHive.model.user.UserNeo4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing User entities and their relationships in Neo4j.
 */
@Repository
public interface UserNeo4jRepository extends Neo4jRepository<UserNeo4j,String> {

    /**
     * Retrieves a user's game library with pagination based on the PLAYED relationship.
     * @param userId The ID of the user.
     * @param pageable Pagination details.
     * @return A Page of LibraryGameDTO.
     */
    @Query(value = "MATCH (u:User {id: $userId})-[r:PLAYED]->(g:Game) " +
            "RETURN g.id AS id, g.name AS name, g.image AS image, g.achievements as achievements, " +
            "r.hoursPlayed AS hoursPlayed, r.achievements AS achievementsObtained "+
            "SKIP $skip LIMIT $limit",
            countQuery = "MATCH (u:User {id: $userId})-[r:PLAYED]->(g:Game) RETURN count(g)"
    )
    Page<LibraryGameDTO> findLibraryById(String userId, Pageable pageable);

    /**
     * Saves a game to the user's library or updates the existing PLAYED relationship metrics.
     * @param userId The ID of the user.
     * @param gameId The ID of the game.
     * @param hoursPlayed Total hours played.
     * @param achievements Total achievements unlocked.
     * @return True if the operation was successful.
     */
    @Query("MATCH (u:User {id: $userId}) " +
            "MATCH (g:Game {id: $gameId}) " +
            "WHERE $achievements <= g.achievements " +
            "MERGE (u)-[r:PLAYED]->(g) " +
            "SET r.hoursPlayed = $hoursPlayed, " +
            "    r.achievements = $achievements " +
            "RETURN count(u) > 0")
    boolean saveGameInLibrary(String userId, String gameId, Double hoursPlayed, Integer achievements);

    /**
     * Removes a game from a user's library and returns the amount of playtime that was recorded.
     * @param userId The ID of the user.
     * @param gameId The ID of the game.
     * @return An Optional containing the registered playtime before deletion.
     */
    @Query("MATCH (u:User {id: $userId})-[r:PLAYED]->(g:Game {id: $gameId}) " +
            "WITH r.hoursPlayed AS playtime, r " +
            "DELETE r " +
            "RETURN playtime")
    Optional<Double> removeGameAndGetPlaytime(String userId, String gameId);

    /**
     * Retrieves a paginated list of a user's friends.
     * @param userId The ID of the user.
     * @param pageable Pagination details.
     * @return A Page of FriendDTO.
     */
    @Query(value = "MATCH (u1:User {id: $userId})-[r:FRIENDS_WITH]->(u2:User) " +
            "RETURN u2.id as id, u2.username as username, u2.pfpURL as pfpURL " +
            "SKIP $skip LIMIT $limit",
            countQuery = "MATCH (u1:User {id: $userId})-[r:FRIENDS_WITH]->(u2:User) RETURN count(u2)")
    Page<FriendDTO> findUsersFriends(String userId, Pageable pageable);

    /**
     * Retrieves all friend IDs for a specific user.
     * @param userId The ID of the user.
     * @return A list of friend IDs.
     */
    @Query("MATCH (u1:User {id: $userId})-[r:FRIENDS_WITH]->(u2:User) " +
            " RETURN u2.id as id")
    List<String> findAllUsersFriend(String userId);

    /**
     * Removes a friendship relationship between two users.
     * @param userId The ID of the first user.
     * @param friendId The ID of the friend to remove.
     * @return True if the relationship was deleted successfully.
     */
    @Query("MATCH (u1:User {id: $userId})-[r:FRIENDS_WITH]-(u2:User {id: $friendId}) " +
            "DELETE r " +
            "RETURN count(u1) >0"
    )
    boolean removeFriendById(String userId, String friendId);

    /**
     * Checks if a friendship relationship exists between two users.
     * @param userId1 The ID of the first user.
     * @param userId2 The ID of the second user.
     * @return True if the users are friends.
     */
    @Query("MATCH p=(u1:User {id: $userId1}) - [r:FRIENDS_WITH] -" +
            " (u2:User {id: $userId2}) " +
            "RETURN count(p) > 0")
    boolean checkFriendshipExistence(String userId1, String userId2);

    /**
     * Creates a mutual friendship relationship between two users.
     * @param userId1 The ID of the first user.
     * @param userId2 The ID of the second user.
     * @return True if the relationship was successfully created.
     */
    @Query("MATCH (u1:User {id: $userId1}) " +
            "MATCH (u2:User {id: $userId2}) " +
            "MERGE (u1)-[:FRIENDS_WITH]->(u2) " +
            "MERGE (u2)-[:FRIENDS_WITH]->(u1) " +
            "RETURN count(u1) > 0")
    boolean createFriendship(String userId1, String userId2);

    /**
     * Deletes a user node and retrieves all their library relationships (hours played for games) prior to deletion.
     * @param userId The ID of the user to delete.
     * @return A list of lightweight game DTOs associated with the deleted user.
     */
    @Query("MATCH (u:User {id: $userId}) " +
            "OPTIONAL MATCH (u)-[r:PLAYED]->(g:Game) " +
            "WITH u, " +
            "     g.id AS id, " +
            "     r.hoursPlayed AS hoursPlayed " +
            "DETACH DELETE u " +
            "WITH id, hoursPlayed " +
            "WHERE id IS NOT NULL " +
            "RETURN id, hoursPlayed")
    List<LibraryGameLightDTO> deleteUserAndRetrieveLibrary(String userId);

    // INTERESTING QUERIES ===================================================

    /**
     * Recommends friends based on mutual connections.
     * @param userId The ID of the user.
     * @param limit Maximum number of recommendations to return.
     * @return A list of FriendRecommendationDTO based on mutual friends.
     */
    @Query("MATCH (u:User {id: $userId})-[:FRIENDS_WITH]->(mutual:User)-[:FRIENDS_WITH]->(suggested:User) " +
            "WHERE u <> suggested AND NOT (u)-[:FRIENDS_WITH]-(suggested) " +
            "RETURN suggested.id as userId, suggested.username AS username, suggested.pfpURL as pfpURL, count(mutual) AS mutualFriendsCount " +
            "ORDER BY mutualFriendsCount DESC " +
            "LIMIT $limit")
    List<FriendRecommendationDTO> getFriendRecommendations(String userId, int limit);

    /**
     * Finds "Gaming Twins" by calculating Jaccard similarity based on shared game libraries.
     * @param userId The ID of the user.
     * @param limit Maximum number of twins to return.
     * @return A list of GamingTwinDTO containing users with high similarity.
     */
    @Query("MATCH (u1:User {id: $userId}) " +
            "WITH u1, COUNT { (u1)-[:PLAYED]->(:Game) } AS u1Total " +
            "MATCH (u1)-[:PLAYED]->(g:Game)<-[:PLAYED]-(u2:User) " +
            "WITH u2, u1Total, count(g) AS sharedGames " +
            "WITH u2, u1Total, sharedGames, COUNT { (u2)-[:PLAYED]->(:Game) } AS u2Total " +
            "WITH u2, sharedGames, (u1Total + u2Total - sharedGames) AS unionGames " +
            "RETURN u2.id as userId, u2.username AS username, toFloat(sharedGames) / unionGames AS jaccardSimilarity," +
            " u2.pfpURL as pfpURL " +
            "ORDER BY jaccardSimilarity DESC " +
            "LIMIT $limit")
    List<GamingTwinDTO> getGamingTwins(String userId, int limit);

}