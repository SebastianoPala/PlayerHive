package com.unipi.PlayerHive.repository.games;

import com.unipi.PlayerHive.DTO.analytics.GenreStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.OsPlatformStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.ReleaseYearStatsDTO;
import com.unipi.PlayerHive.DTO.containers.ReviewIdContainerDTO;
import com.unipi.PlayerHive.DTO.games.*;
import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import com.unipi.PlayerHive.model.game.Game;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Game entities in MongoDB.
 */
@Repository
public interface GameRepository extends MongoRepository<Game, String> {

    /**
     * Searches for games based on a case-insensitive partial match on the game name.
     * @param gameName The partial name of the game to search.
     * @param pageable Pagination settings.
     * @return A slice containing GameSearchDTO elements.
     */
    @Query("{ 'name': { $regex: '^?0'} }" +
            "{ '$project': { 'id': '$_id', 'name': 1, 'price': 1, 'discount':1, 'finalPrice': 1,'imageURL':1 } }")
    Slice<GameSearchDTO> searchByNameContaining(String gameName, Pageable pageable);

    /**
     * Checks if a game exists by its name.
     * @param name The game name.
     * @return True if the game exists, false otherwise.
     */
    boolean existsByName(String name);

    /**
     * Retrieves lightweight game details excluding all reviews.
     * @param gameId The ID of the game.
     * @return An Optional containing the lightweight game entity.
     */
    @Query("{ '_id': ?0 }" +
            "{ '$project': { 'allReviews': 0 }}")
    Optional<Game> findByIdLight(String gameId);

    /**
     * Increments the total hours played and number of players for a specific game.
     * @param userId The ID of the game.
     * @param playtimeToAdd Total hours to add.
     * @param userNumberToAdd Amount of users to increment by.
     * @return Number of documents updated.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'totalHoursPlayed': ?1, 'numPlayers': ?2 } }")
    int updateGameStats(String userId, float playtimeToAdd, int userNumberToAdd);

    /**
     * Adds a review to a game, updates recent review limits, and adjusts scoring metrics.
     * @param gameId The ID of the game.
     * @param oldReview The ObjectId of the review being added.
     * @param recentReview The DTO containing the recent review data.
     * @param score The review score to add to the total sum.
     * @return Number of documents updated.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$push': { " +
            "    'allReviews': {" +
            "        '$each': [?1]," +
            "        '$position': 0" +
            "    }, " +
            "    'recentReviews': { " +
            "        '$each': [?2], " +
            "        '$position': 0, " +
            "        '$slice': 25 " +
            "    } " +
            "}, " +
            "'$inc' :{ 'countScore': 1, 'sumScore' : ?3} }")
    int addReviewToGame(String gameId, ObjectId oldReview, GameReviewDTO recentReview, float score);

    /**
     * Removes a review from a game and subtracts its score from total stats.
     * @param gameId The ObjectId of the game.
     * @param reviewId The ID of the review to delete.
     * @param score The score to remove from the total sum.
     * @return Number of documents updated.
     */
    @Query("{ '_id': ?0, 'allReviews': ObjectId(?1) }")
    @Update("{" +
            "  '$inc': { 'countScore': -1, 'sumScore': ?2 }," +
            "  '$pull': {" +
            "    'allReviews': ObjectId(?1)," +
            "    'recentReviews': { '_id': ObjectId(?1) }" +
            "  }" +
            "}")
    int deleteReviewFromGame(ObjectId gameId, String reviewId, Float score);

    /**
     * Retrieves a paginated slice of reviews for a game.
     * @param gameId The ID of the game.
     * @param skip Number of items to skip.
     * @param limit Max number of items to retrieve.
     * @return A container of review IDs.
     */
    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { '_id': 0, 'reviews': { '$slice': ['$allReviews', ?1, ?2] }, 'countScore':1 } }"
    })
    ReviewIdContainerDTO getGameReviews(String gameId, int skip, int limit);

    /**
     * Retrieves just the name and image URL of a game by its ID.
     * @param gameId The ID of the game.
     * @return An Optional GameNameImageDTO.
     */
    @Query("{ '_id': ?0 }" +
            "{ '$project': { '_id' : 0, 'name' : 1, 'image' : 1 } }")
    Optional<GameNameImageDTO> getGameNameAndImageById(String gameId);

    // INTERESTING QUERIES ===========================================

    /**
     * Finds games with a high quality-to-price ratio based on average rating and cost.
     * @param minReviews Minimum number of reviews needed to be considered.
     * @param minPrice Minimum game price.
     * @param maxPrice Maximum game price.
     * @param minRating Minimum average rating needed.
     * @return A list of GameStatsDTO sorted by value.
     */
    @Aggregation(pipeline = {
            "{ $match: { countScore: { $gt: ?0 } } }",
            "{ $project: { _id: 1, name: 1, price: 1, discount: 1, image: 1, avgRating: { $divide: ['$sumScore', '$countScore'] }, finalPrice: 1, genres: 1} }",
            "{ $match: { finalPrice: { $gte: ?1, $lte: ?2 }, avgRating: { $gte: ?3 } } }",
            "{ $addFields: { qualityPerPrice: { $cond: [ { $eq: ['$finalPrice', 0] }, 999999, { $divide: ['$avgRating', '$finalPrice'] } ] } } }",
            "{ $sort: { qualityPerPrice: -1 } }",
            "{ $limit: 15 }"
    })
    List<GameStatsDTO> getQualityToPriceGames(int minReviews, double minPrice, double maxPrice, double minRating);

    /**
     * Finds games providing high playtime value relative to their price.
     * @param minPlayers Minimum number of players recorded.
     * @param minPrice Minimum game price.
     * @param maxPrice Maximum game price.
     * @param minAvgTime Minimum average time played per player.
     * @return A list of GameInvestmentDTO sorted by best value.
     */
    @Aggregation(pipeline = {
            "{ $match: { numPlayers: { $gt: ?0 } } }",
            "{ $project: { _id: 1, name: 1, price: 1, discount: 1, finalPrice: 1, image: 1, numPlayers: 1, avgTimePlayed: { $divide: ['$totalHoursPlayed', '$numPlayers'] } , genres: 1 } }",
            "{ $match: { finalPrice: { $gte: ?1, $lte: ?2 }, avgTimePlayed: { $gte: ?3 } } }",
            "{ $addFields: { valueForMoney: { $cond: [ { $eq: ['$finalPrice', 0] }, 999999, { $divide: ['$avgTimePlayed', '$finalPrice'] } ] } } }",
            "{ $sort: { valueForMoney: -1 } }",
            "{ $limit: 15 }"
    })
    List<GameInvestmentDTO> getTimeToPriceGames(int minPlayers, double minPrice, double maxPrice, double minAvgTime);

    /**
     * Identifies the most discussed games based on the timing density of recent reviews.
     * @return A list of GameStatsDTO sorted by highest review density over time.
     */
    @Aggregation(pipeline = {

            "{ $match: { 'recentReviews.1': { $exists: true } } }",
            "{ $project: { " +
                    "_id: 1, " +
                    "    name: 1, " +
                    "    price: 1, " +
                    "    discount: 1, " +
                    "finalPrice: 1" +
                    "    image: 1, " +
                    "    sumScore: 1, " +
                    "    countScore: 1, " +
                    "genres: 1," +
                    "    timeDistanceMs: { " +
                    "      $subtract: [ " +
                    "        { $max: '$recentReviews.timestamp' }, " +
                    "        { $min: '$recentReviews.timestamp' } " +
                    "      ] " +
                    "    } " +
                    "} }",
            "{ $sort: { timeDistanceMs: 1 } }",
            "{ $limit: 15 }",
            "{ $addFields: { avgRating: { $cond: [ { $eq: ['$countScore', 0] }, 0, { $divide: ['$sumScore', '$countScore'] } ] } } }"
    })
    List<GameStatsDTO> findMostDiscussedGames();

    /**
     * Retrieves the top-rated games that meet a required review threshold.
     * @param minReviews Minimum number of reviews needed.
     * @return A list of top-rated GameStatsDTO.
     */
    @Aggregation(pipeline = {

            "{ $match: { countScore: { $gte: ?0 } } }",

            "{ $project: { _id: 1, name: 1, price: 1, discount: 1 , finalPrice: 1, avgRating: { $divide: ['$sumScore', '$countScore'] }, image: 1, genres: 1 } }",

            "{ $sort: { avgRating: -1, countScore: -1 } }",

            "{ $limit: 15 }",
    })
    List<GameStatsDTO> getTopRatedGames(int minReviews);

    /**
     * Retrieves the most recently released games for the home page.
     * it relies on a descending index on {@code release_date}, so it touches only the newest
     * documents instead of scanning and sorting the whole catalogue on every load.
     * @return A list of the 15 newest games projected into GameStatsDTO.
     */
    @Query(value = "{ 'release_date': { $ne: null } }",
            fields = "{ '_id': 1, 'name': 1, 'price': 1, 'discount': 1, 'finalPrice': 1, 'image': 1, 'genres': 1 }",
            sort = "{ 'release_date': -1 }")
    List<NewGameInfoDTO> getNewReleases(Pageable pageable);

    //admin analytics

    /**
     * Admin analytics query that aggregates average scores, playtime, and popularity grouped by genre.
     * @return A list of GenreStatsDTO with genre analytics.
     */
    @Aggregation(pipeline = {
            "{ $match: { countScore: { $gt: 0 }, numPlayers: { $gt: 0 } } }",
            "{ $project: { genres: 1, avgScore: { $divide: ['$sumScore', '$countScore'] }, avgHoursPerPlayer: { $divide: ['$totalHoursPlayed', '$numPlayers'] } } }",
            "{ $unwind: '$genres' }",
            "{ $group: { _id: '$genres', avgScore: { $avg: '$avgScore' }, avgHoursPerPlayer: { $avg: '$avgHoursPerPlayer' }, totalGames: { $sum: 1 } } }",
            "{ $sort: { avgScore: -1 } }",
            "{ $project: { _id: 0, genre: '$_id', avgScore: { $round: ['$avgScore', 2] }, avgHoursPerPlayer: { $round: ['$avgHoursPerPlayer', 2] }, totalGames: 1 } }"
    })
    List<GenreStatsDTO> getGenreStats();

    /**
     * Admin analytics query that aggregates average game scores and counts grouped by OS compatibility quantity.
     * @return A list of OsPlatformStatsDTO containing platform metrics.
     */
    @Aggregation(pipeline = {
            "{ $match: { countScore: { $gt: 0 } } }",
            "{ $project: { osCount: { $size: '$supportedOS' }, avgScore: { $divide: ['$sumScore', '$countScore'] } } }",
            "{ $group: { _id: '$osCount', avgScore: { $avg: '$avgScore' }, totalGames: { $sum: 1 } } }",
            "{ $sort: { _id: 1 } }",
            "{ $project: { _id: 0, osCount: '$_id', avgScore: { $round: ['$avgScore', 2] }, totalGames: 1 } }"
    })
    List<OsPlatformStatsDTO> getOsPlatformStats();

    /**
     * Admin analytics query to analyze trends over the years based on game scores and release quantities.
     * @return A list of ReleaseYearStatsDTO with yearly release statistics.
     */
    @Aggregation(pipeline = {
            "{ $match: { countScore: { $gt: 0 }, release_date: { $ne: null } } }",
            "{ $project: { releaseYear: { $year: '$release_date' }, avgScore: { $divide: ['$sumScore', '$countScore'] } } }",
            "{ $group: { _id: '$releaseYear', avgScore: { $avg: '$avgScore' }, totalGames: { $sum: 1 } } }",
            "{ $sort: { _id: 1 } }",
            "{ $project: { _id: 0, releaseYear: '$_id', avgScore: { $round: ['$avgScore', 2] }, totalGames: 1 } }"
    })
    List<ReleaseYearStatsDTO> getReleaseYearStats();
}
