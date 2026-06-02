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

@Repository
public interface UserRepository extends MongoRepository<User,String> {

    @Aggregation(pipeline = {
            "{ '$match': { '$or': [ { 'username': ?0 }, { 'email': ?1 } ] } }",
            "{ '$limit': 1 }",
            "{ '$project': { 'username': 1, 'email': 1, '_id': 0 } }"
    })
    Optional<User> findLightByUsernameOrEmail(@NotBlank String username, @NotBlank @Email String email);

    @Query("{ 'username': { $regex: ?0, $options: 'i' } }" +
            "{ '$project': { 'id': '$_id', 'username': 1, 'role': 1, 'pfpURL':1 } }")
    Slice<UserSearchDTO> searchByUsernameContaining(String username, Pageable pageable);

    @Query("{ '_id' : ?0, 'friendRequests.user_id' : { '$ne': ?1 } }")
    @Update("{ '$push' : { 'friendRequests' : { '$each' : [ ?2 ], '$position' : 0 } }, '$inc' : { 'requestsNum' : 1 } }")
    int addFriendRequest(String targetUserId, ObjectId senderUserId, FriendRequestMongoDTO request);

    @Query("{ '_id' : ?0, 'friendRequests.user_id' : ?1 }")
    @Update("{ '$pull' : { 'friendRequests' : { 'user_id' : ?1 } }, " +
            "  '$inc' : { 'friends' : 1, 'requestsNum' : -1 } }")
    int acceptFriendRequest(String userId, ObjectId userToAccept);

    @Query("{ '_id' : ?0, 'friendRequests.user_id' : ?1 }")
    @Update("{ '$pull' : { 'friendRequests' : { 'user_id' : ?1 } }, " +
            "  '$inc' : { 'requestsNum' : -1 } }")
    int removeFriendRequest(String userId, ObjectId userToRemove);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { 'friendRequests': { '$slice': ['$friendRequests', ?1, ?2] } } }"
    })
    FriendRequestMongoContainerDTO findFriendRequestsById(String id, int skip, int limit);

    @Query("{ '_id' : ?0 }")
    @Update("{ '$inc' : { 'friends' : ?1 } }")
    int editFriendCounter(String userId, int quantity);

    @Query("{ '_id' : { $in : ?0 } }")
    @Update("{ '$inc' : { 'friends' : -1 } }")
    int decrementFriendCounterForUsers(List<String> userIds);

    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'hoursPlayed': ?1, 'numGames': ?2 } }")
    int updateUserStats(String userId, float playtimeToAdd, int gameNumberToAdd);

    @Query(value = "{ 'email': ?0 }", fields = "{ '_id:' 1, 'username' : 1, 'role': 1, 'requestsNum': 1, 'pfpURL': 1}")
    User findByEmail(String email);

    @Query(value = "{ '_id': ?0 }",  fields = "{ '_id:' 1, 'username' : 1, 'role': 1, 'requestsNum': 1, 'pfpURL': 1}")
    User findByIdLean(String id);

    //@Query(value = "{ '_id': ?0 }", fields = "{ 'reviewIds': 0, 'friendRequests': 0 }")
    @Query(value = "{ '_id': ?0 }", fields = "{ 'reviewIds': 0, 'friendRequests': { $slice: [0, 10] } }")
    OwnProfileMongoDTO getOwnProfile(String id);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { 'reviews': { '$slice': ['$reviewIds', ?1, ?2] } , 'reviewsNum' : { '$size': '$reviewIds'} }  }"
    })
    OldUserReviewContainerDTO getUserReviews(String userId, int skip, int limit);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': ?0 } }",
            "{ '$project': { 'reviews': '$reviewIds' } }"
    })
    OldUserReviewContainerDTO getAllUserReviews(String userId);

    // push a new {reviewId, gameId} pair into the user's reviewIds array when they write a review
    @Query("{ '_id': ?0 }")
    @Update("{ '$push': { 'reviewIds': { '$each': [ ?1 ], '$position': 0 } } }")
    void addReviewToUser(String userId, OldUserReviewDTO review);

    // pull the review entry out of reviewIds when the review is deleted
    @Query("{ '_id': ?0 }")
    @Update("{ '$pull': { 'reviewIds': { 'review_id': ?1 } } }")
    void removeReviewFromUser(String userId, org.bson.types.ObjectId reviewId);

    @Query(value = "{ '_id': ?0, 'reviewIds.game_id': ?1 }", exists = true)
    boolean hasUserAlreadyReviewed(String userId, ObjectId  gameId);

    @Query("{ 'reviewIds.game_id': ?0 }")
    @Update("{ '$pull': { 'reviewIds': { 'game_id': ?0 } } }")
    long removeAllGameReviewsFromUsers(ObjectId gameId);

    @Query("{ '_id': { '$in': ?0 } }")
    @Update("{ '$pull': { 'reviewIds': { 'game_id': ?1 } } }")
    long removeReviewFromUsersByGame(List<String> userIds, ObjectId gameId);


    // INTERESTING QUERIES ============================================

    @Aggregation(pipeline = {
            "{ $match: { numGames: { $gt: ?0 }, hoursPlayed: { $gt: ?1 } } }",
            "{ $project: { " +
                        "_id: 1," +
                    "    username: 1, " +
                    "    role: 1, " +
                    "    totalHours: '$hoursPlayed', " +
                    "    numGames: 1, " +
                    "    avgHoursPerGame: { $round: [{ $divide: ['$hoursPlayed', '$numGames'] }, 1] } " +
                    "} }",
            "{ $sort: { avgHoursPerGame: -1 } }",
            "{ $limit: 15 }"
        })
    List<PlayerStatsDTO> findHardcoreGamers(int minGames, double minHours);

    @Aggregation(pipeline = {

            "{ $match: { reviewIds: { $exists: true, $not: { $size: 0 } } } }",

            "{ $project: { " +
                        "_id: 1," +
                    "    username: 1, " +
                    "    role: 1, " +
                    "    pfpURL: 1, " +
                    "    numGames: 1, " +
                    "    numReviews: { $size: '$reviewIds' }, " +
                    // if numGames is 0, we set a very high artificial score ( review number * 100)
                    // otherwise we just do reviews / games
                    "    warriorRatio: { $cond: [ " +
                    "        { $eq: ['$numGames', 0] }, " +
                    "        { $multiply: [{ $size: '$reviewIds' }, 100] }, " +
                    "        { $divide: [{ $size: '$reviewIds' }, '$numGames'] } " +
                    "    ] } " +
                    "} }",

            "{ $match: { warriorRatio: { $gt: ?0 } } }",

            "{ $sort: { warriorRatio: -1, numReviews: -1 } }",
            "{ $limit: 15 }"
    })
    List<KeyboardWarriorDTO> getKeyboardWarriors(double warriorRatio);

    @Aggregation(pipeline = {
            "{ $match: { numGames: { $gt: 0 }} }",

            "{ $project: { " +
                        "_id: 1," +
                    "    username: 1, " +
                    "    pfpURL: 1, " +
                    "    role: 1, " +
                    "    numGames: 1, " +
                    "    registrationDate: 1, " +
                    "    gamesPerDay: { $divide: ['$numGames', { $divide: [ { $subtract: ['$$NOW', '$registrationDate'] } , 86400000] }] }  " +
                    "} }",

            "{ $sort: { gamesPerDay: -1 } }",
            "{ $limit: 15 }"
    })
    List<ActiveGamerDTO> getMostActiveGamers();
}
