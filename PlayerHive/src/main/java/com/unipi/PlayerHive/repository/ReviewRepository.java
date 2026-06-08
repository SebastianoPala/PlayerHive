package com.unipi.PlayerHive.repository;

import com.unipi.PlayerHive.DTO.reviews.GameReviewDTO;
import com.unipi.PlayerHive.DTO.reviews.ReviewScoreDTO;
import com.unipi.PlayerHive.DTO.reviews.UserReviewDTO;
import com.unipi.PlayerHive.model.Review;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing Review entities in MongoDB.
 */
@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    /**
     * Removes a review by its unique identifier.
     * @param reviewId The ID of the review to remove.
     * @return The removed Review object.
     */
    Review removeById(String reviewId);

    /**
     * Finds game reviews matching the provided list of IDs, sorted by descending timestamp.
     * @param reviewIds List of review IDs to retrieve.
     * @return List of GameReviewDTO matching the IDs.
     */
    @Query(value = "{ '_id': { $in: ?0 } }", sort = "{ 'timestamp': -1 }")
    List<GameReviewDTO> findGameReviewsByIdIn(List<String> reviewIds);

    /**
     * Finds user reviews matching the provided list of IDs, sorted by descending timestamp.
     * @param reviewIds List of review IDs to retrieve.
     * @return List of UserReviewDTO matching the IDs.
     */
    @Query(value = "{ '_id': { $in: ?0 } }", sort = "{ 'timestamp': -1 }")
    List<UserReviewDTO> findUserReviewsByIdIn(List<String> reviewIds);

    /**
     * Removes multiple reviews matching the provided list of IDs.
     * @param reviewIds List of review IDs to remove.
     * @return The number of reviews removed.
     */
    long removeByIdIn(List<String> reviewIds);

    /**
     * Removes a specific review ensuring it belongs to the requesting user.
     * @param reviewId The ID of the review to remove.
     * @param requesterId The ObjectId of the user requesting the deletion.
     * @return The removed Review object.
     */
    Review removeByIdAndUserId(String reviewId, ObjectId requesterId);

    /**
     * Retrieves review scores for the given review IDs.
     * @param reviewIds List of review IDs.
     * @return List of ReviewScoreDTO containing score data.
     */
    @Query("{ '_id': { $in: ?0 } }")
    List<ReviewScoreDTO> findGameScoreByIdIn(List<String> reviewIds);

    /**
     * Updates game name and image for a specific set of review IDs.
     * @param reviewIds List of review IDs to update.
     * @param gameName The new game name.
     * @param gameUrl The new game image URL.
     * @return The number of reviews updated.
     */
    @Query("{ '_id': { $in: ?0 } }")
    @Update("{ '$set': { 'game_name': ?1 , 'game_image': ?2 } }")
    long editInfoIn(List<String> reviewIds, String gameName, String gameUrl);

}
