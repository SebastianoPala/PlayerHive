package com.unipi.PlayerHive.repository.users;

import com.unipi.PlayerHive.DTO.containers.FriendRequestMongoContainerDTO;
import com.unipi.PlayerHive.DTO.containers.OldUserReviewContainerDTO;
import com.unipi.PlayerHive.DTO.reviews.OldUserReviewDTO;
import com.unipi.PlayerHive.DTO.users.*;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestMongoDTO;
import com.unipi.PlayerHive.model.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing User entities in MongoDB.
 */
@Repository
public interface UserRepository extends MongoRepository<User,String> {

    /**
     * Finds a lightweight representation of a user by username or email.
     * @param username The username to search for.
     * @param email The email to search for.
     * @return An Optional containing the matched User.
     */
    @Aggregation(pipeline = {
            "{ '$match': { '$or': [ { 'username': ?0 }, { 'email': ?1 } ] } }",
            "{ '$limit': 1 }",
            "{ '$project': { 'username': 1, 'email': 1, '_id': 0 } }"
    })
    Optional<User> findLightByUsernameOrEmail(@NotBlank String username, @NotBlank @Email String email);

    /**
     * Searches for users by a partial, case-insensitive username match.
     * @param username The partial username to search.
     * @param pageable Pagination details.
     * @return A slice of UserSearchDTOs matching the criteria.
     */
    @Query("{ 'username': { $regex: '^?0' } }" +
            "{ '$project': { 'id': '$_id', 'username': 1, 'pfpURL':1 } }")
    Slice<UserSearchDTO> searchByUsernameContaining(String username, Pageable pageable);

    /**
     * Adds a friend request to a target user's profile.
     * @param targetUserId The ID of the user receiving the request.
     * @param senderUserId The ObjectId of the user sending the request.
     * @param request The friend request data to add.
     * @return The number of documents updated.
     */
    @Query("{ '_id' : ?0, 'friendRequests.user_id' : { '$ne': ?1 } }")
    @Update("{ '$push' : { 'friendRequests' : { '$each' : [ ?2 ], '$position' : 0 } }, '$inc' : { 'requestsNum' : 1 } }")
    int addFriendRequest(String targetUserId, ObjectId senderUserId, FriendRequestMongoDTO request);

    /**
     * Accepts a friend request by removing it from the pending list and incrementing the friends count.
     * @param userId The ID of the user accepting the request.
     * @param userToAccept The ObjectId of the user whose request is accepted.
     * @return The number of documents updated.
     */
    @Query("{ '_id' : ?0, 'friendRequests.user_id' : ?1 }")
    @Update("{ '$pull' : { 'friendRequests' : { 'user_id' : ?1 } }, " +
            "  '$inc' : { 'friends' : 1, 'requestsNum' : -1 } }")
    int acceptFriendRequest(String userId, ObjectId userToAccept);

    /**
     * Denies and removes a friend request from a user's profile.
     * @param userId The ID of the user rejecting the request.
     * @param userToRemove The ObjectId of the user whose request is denied.
     * @return The number of documents updated.
     */
    @Query("{ '_id' : ?0, 'friendRequests.user_id' : ?1 }")
    @Update("{ '$pull' : { 'friendRequests' : { 'user_id' : ?1 } }, " +
            "  '$inc' : { 'requestsNum' : -1 } }")
    int removeFriendRequest(String userId, ObjectId userToRemove);

    /**
     * Retrieves paginated friend requests for a specific user.
     * @param id The ID of the user.
     * @param skip Number of items to skip.
     * @param limit Maximum number of items to return.
     * @return A container with the paginated friend requests.
     */
    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { 'friendRequests': { '$slice': ['$friendRequests', ?1, ?2] } } }"
    })
    FriendRequestMongoContainerDTO findFriendRequestsById(String id, int skip, int limit);

    /**
     * Modifies a user's total friends counter by a specific quantity.
     * @param userId The ID of the user.
     * @param quantity The amount to modify the counter by (can be negative).
     * @return The number of documents updated.
     */
    @Query("{ '_id' : ?0 }")
    @Update("{ '$inc' : { 'friends' : ?1 } }")
    int editFriendCounter(String userId, int quantity);

    /**
     * Decrements the friends counter by 1 for multiple users.
     * @param userIds List of user IDs.
     * @return The number of documents updated.
     */
    @Query("{ '_id' : { $in : ?0 } }")
    @Update("{ '$inc' : { 'friends' : -1 } }")
    int decrementFriendCounterForUsers(List<String> userIds);

    /**
     * Updates the gameplay stats (playtime and total games) for a user.
     * @param userId The ID of the user.
     * @param playtimeToAdd The number of hours to add.
     * @param gameNumberToAdd The number of games to add to the counter.
     * @return The number of documents updated.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'hoursPlayed': ?1, 'numGames': ?2 } }")
    int updateUserStats(String userId, float playtimeToAdd, int gameNumberToAdd);

    /**
     * Retrieves lightweight user data by email.
     * @param email The user's email.
     * @return The retrieved User object.
     */
    @Query(value = "{ 'email': ?0 }", fields = "{ '_id:' 1, 'username' : 1, 'role': 1, 'requestsNum': 1, 'pfpURL': 1}")
    User findByEmail(String email);

    /**
     * Retrieves lightweight user data by their ID.
     * @param id The user's ID.
     * @return The retrieved User object.
     */
    @Query(value = "{ '_id': ?0 }",  fields = "{ '_id:' 1, 'username' : 1, 'role': 1, 'requestsNum': 1, 'pfpURL': 1}")
    User findByIdLean(String id);

    /**
     * Retrieves the user's complete profile data, excluding review IDs and limiting the friend requests preview.
     * @param id The user's ID.
     * @return OwnProfileMongoDTO representing the user's profile.
     */
    //@Query(value = "{ '_id': ?0 }", fields = "{ 'reviewIds': 0, 'friendRequests': 0 }")
    @Query(value = "{ '_id': ?0 }", fields = "{ 'reviewIds': 0, 'friendRequests': { $slice: [0, 10] } }")
    OwnProfileMongoDTO getOwnProfile(String id);

    /**
     * Retrieves a paginated chunk of a user's reviews.
     * @param userId The ID of the user.
     * @param skip Number of items to skip.
     * @param limit Maximum number of items to return.
     * @return A container of the user's review DTOs.
     */
    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { 'reviews': { '$slice': ['$reviewIds', ?1, ?2] } , 'reviewsNum' : { '$size': '$reviewIds'} }  }"
    })
    OldUserReviewContainerDTO getUserReviews(String userId, int skip, int limit);

    /**
     * Pushes a new {reviewId, gameId} pair into the user's reviewIds array when they write a review.
     * @param userId The ID of the user.
     * @param review The review reference object to add.
     */
    // push a new {reviewId, gameId} pair into the user's reviewIds array when they write a review
    @Query("{ '_id': ?0 }")
    @Update("{ '$push': { 'reviewIds': { '$each': [ ?1 ], '$position': 0 } } }")
    void addReviewToUser(String userId, OldUserReviewDTO review);

    /**
     * Removes a review reference from the user's reviewIds array when deleted.
     * @param userId The ID of the user.
     * @param reviewId The ObjectId of the review to remove.
     */
    // pull the review entry out of reviewIds when the review is deleted
    @Query("{ '_id': ?0 }")
    @Update("{ '$pull': { 'reviewIds': { 'review_id': ?1 } } }")
    void removeReviewFromUser(String userId, ObjectId reviewId);

    /**
     * Checks if a user has already reviewed a given game.
     * @param userId The ID of the user.
     * @param gameId The ObjectId of the game.
     * @return True if the review exists, otherwise false.
     */
    @Query(value = "{ '_id': ?0, 'reviewIds.game_id': ?1 }", exists = true)
    boolean hasUserAlreadyReviewed(String userId, ObjectId  gameId);

    /**
     * Removes a specific game's review from a targeted list of users.
     * @param userIds List of user IDs.
     * @param gameId The ObjectId of the game.
     * @return The number of documents updated.
     */
    @Query("{ '_id': { '$in': ?0 } }")
    @Update("{ '$pull': { 'reviewIds': { 'game_id': ?1 } } }")
    long removeReviewFromUsersByGame(List<String> userIds, ObjectId gameId);

    // INTERESTING QUERIES ============================================

    /**
     * Aggregation query to find hardcore gamers matching minimum game counts and playtime, sorted by average playtime.
     * @param minGames Minimum number of games owned.
     * @param minHours Minimum number of total hours played.
     * @return A list of PlayerStatsDTO representing hardcore gamers.
     */
    @Aggregation(pipeline = {
            "{ $match: { numGames: { $gt: ?0 }, hoursPlayed: { $gt: ?1 } } }",
            "{ $project: { " +
                    "_id: 1," +
                    "   username: 1, " +
                    "    pfpURL: 1, " +
                    "    totalHours: '$hoursPlayed', " +
                    "    numGames: 1, " +
                    "    avgHoursPerGame: { $round: [{ $divide: ['$hoursPlayed', '$numGames'] }, 1] } " +
                    "} }",
            "{ $sort: { avgHoursPerGame: -1 } }",
            "{ $limit: 15 }"
    })
    List<PlayerStatsDTO> findHardcoreGamers(int minGames, double minHours);

    /**
     * Aggregation query to find "Keyboard Warriors", calculating the ratio of reviews to owned games.
     * @param warriorRatio The minimum review-to-game ratio threshold.
     * @return A list of KeyboardWarriorDTO.
     */
    @Aggregation(pipeline = {

            "{ $match: { reviewIds: { $exists: true, $not: { $size: 0 } } } }",

            "{ $project: { " +
                    "_id: 1," +
                    "    username: 1, " +
                    "    pfpURL: 1, " +
                    "    numGames: 1, " +
                    "    numReviews: { $size: '$reviewIds' }, " +
                    // if numGames is 0, we set a very high artificial score ( review number * 100)
                    // otherwise we just do reviews / games
                    "    warriorRatio: { $cond: [ " +
                    "       { $eq: ['$numGames', 0] }, " +
                    "        { $multiply: [{ $size: '$reviewIds' }, 100] }, " +
                    "        { $divide: [{ $size: '$reviewIds' }, '$numGames'] } " +
                    "    ] } " +
                    "} }",

            "{ $match: { warriorRatio: { $gt: ?0 } } }",

            "{ $sort: { warriorRatio: -1, numReviews: -1 } }",
            "{ $limit: 15 }"
    })
    List<KeyboardWarriorDTO> getKeyboardWarriors(double warriorRatio);

    /**
     * Aggregation query to find the most active gamers based on the number of games played per day since registration.
     * @return A list of ActiveGamerDTO representing the most active users.
     */
    @Aggregation(pipeline = {
            "{ $match: { numGames: { $gt: 0 }} }",

            "{ $project: { " +
                    "_id: 1," +
                    "    username: 1, " +
                    "    pfpURL: 1, " +
                    "    numGames: 1, " +
                    "    registrationDate: 1, " +
                    "    gamesPerDay: { $divide: ['$numGames', { $divide: [ { $subtract: ['$$NOW', '$registrationDate'] } , 86400000] }] }  " +
                    "} }",

            "{ $sort: { gamesPerDay: -1 } }",
            "{ $limit: 15 }"
    })
    List<ActiveGamerDTO> getMostActiveGamers();

}