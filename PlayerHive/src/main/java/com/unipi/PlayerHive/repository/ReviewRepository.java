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


@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    Review removeById(String reviewId);

    @Query(value = "{ '_id': { $in: ?0 } }", sort = "{ 'timestamp': -1 }")
    List<GameReviewDTO> findGameReviewsByIdIn(List<String> reviewIds);

    @Query(value = "{ '_id': { $in: ?0 } }", sort = "{ 'timestamp': -1 }")
    List<UserReviewDTO> findUserReviewsByIdIn(List<String> reviewIds);

    long removeByGameId(ObjectId gameId);
    long removeByUserId(ObjectId userId);

    long removeByIdIn(List<String> reviewIds);

    Review removeByIdAndUserId(String reviewId, ObjectId requesterId);

    @Query("{ '_id': { $in: ?0 } }")
    List<ReviewScoreDTO> findGameScoreByIdIn(List<String> reviewIds);

    @Query("{ '_id': { $in: ?0 } }")
    @Update("{ '$set': { 'game_name': ?1 , 'game_image': ?2 } }")
    long editInfoIn(List<String> reviewIds, String gameName, String gameUrl);
}
