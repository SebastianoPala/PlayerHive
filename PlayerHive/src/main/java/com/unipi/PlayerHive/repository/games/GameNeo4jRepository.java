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

@Repository
public interface GameNeo4jRepository extends Neo4jRepository<GameNeo4j,String>{

    @Query("MATCH (u:User)-[r:PLAYED]->(g:Game {id: $gameId}) " +
            "WITH u, r LIMIT $batchSize " +
            "WITH u.id AS id, r.hoursPlayed AS hoursPlayed, r " +
            "DELETE r " +
            "RETURN id, hoursPlayed")
    List<GameOwnerDTO> deletePlayedEdgesInBatch(String gameId, int batchSize);

    // gets the USER's playtime (if present) and the GAME'S (NOT the user's) achievements
    @Query("MATCH (g:Game {id: $gameId}) " +
            "OPTIONAL MATCH (u:User {id: $userId})-[r:PLAYED]->(g) " +
            "RETURN r.hoursPlayed as hoursPlayed, g.achievements as achievements")
    PlaytimeAchievementsDTO findUserPlaytimeAndGameAchievements(String userId, String gameId);

    // INTERESTING QUERIES ========================================

    // todo fix return values
    //  2. The "Game Recommendation" (Item-Based Collaborative Filtering)
    @Query("MATCH (u:User {id: $userId})-[:FRIENDS_WITH]->(friend:User)-[:PLAYED]->(recGame:Game) " +
            "WHERE NOT (u)-[:PLAYED]->(recGame) " +
            "RETURN recGame.id as gameId, recGame.name AS name, recGame.image as image, " +
            " count(friend) AS friendsWhoPlay " +
            "ORDER BY friendsWhoPlay DESC " +
            "LIMIT $limit")
    List<GameRecommendationDTO> getGameRecommendations(String userId, int limit);

    // 5. Efficient Global Query: "Trending Games Among Friend Groups"
    // todo fix return values
    // todo maybe move to analytics
    // really heavy query
    @Query("MATCH (u1:User)-[:FRIENDS_WITH]->(u2:User) " +
            "WHERE elementId(u1) < elementId(u2) " +
            "MATCH (u1)-[:PLAYED]->(g:Game)<-[:PLAYED]-(u2) " +
            "WITH g.name AS name, count(*) AS socialPlayCount " +
            "WHERE socialPlayCount > $minSocialCount " +
            "RETURN name, socialPlayCount " +
            "ORDER BY socialPlayCount DESC " +
            "LIMIT $limit")
    List<TrendingGameDTO> getTrendingGamesAmongFriends(int limit, int minSocialCount);

    // 8. The "Hidden Gem" Recommendation (Inverse Popularity)
    //todo this feels like the "game recommendation" query :( are they all the same?
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
            "LIMIT 10") // todo better hardcoded or variable?
    List<HiddenGemDTO> getHiddenGems(String userId, int nicheThreshold);

    @Query("MATCH (g:Game {id: $gameId})<-[:PLAYED]-(u:User)-[:PLAYED]->(other:Game) " +
        "WHERE g <> other " +
        "RETURN other.id as gameId, other.name AS name, other.image as image, count(DISTINCT u) AS sharedPlayers " +
        "WHERE sharedPlayers >= $minShared" +
        "ORDER BY sharedPlayers DESC " +
        "LIMIT $limit") // todo better hardcoded or variable?
    List<RelatedGameDTO> getRelatedGames(String gameId, int minShared, int limit);


}
