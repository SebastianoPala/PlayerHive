package com.unipi.PlayerHive.utility.batch;

import com.mongodb.bulk.BulkWriteResult;
import com.unipi.PlayerHive.DTO.games.LibraryGameDTO;
import com.unipi.PlayerHive.DTO.reviews.ReviewScoreDTO;
import com.unipi.PlayerHive.model.game.Game;
import com.unipi.PlayerHive.repository.ReviewRepository;
import com.unipi.PlayerHive.repository.users.UserNeo4jRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class GameConsistencyManager {

    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final ReviewRepository reviewRepository;
    private final MongoTemplate mongoTemplate;

    public GameConsistencyManager(UserRepository userRepository, UserNeo4jRepository userNeo4jRepository, ReviewRepository reviewRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.userNeo4jRepository = userNeo4jRepository;
        this.reviewRepository = reviewRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public long removeUserReviewsFromGames(String userId){
        boolean reviews_left = true;
        int page_size = 1000;
        int step = 0;

        long modified = 0;

        while(reviews_left){
            List<String> userReviews = userRepository.getUserReviews(userId,step,page_size).getReviews().stream()
                    .map(userReviewDTO -> userReviewDTO.getReviewId().toString()).toList();
            step += page_size;

            if(userReviews.isEmpty())
                break;
            else if(userReviews.size() < page_size)
                reviews_left = false;

            List<ReviewScoreDTO> reviews = reviewRepository.findGameScoreByIdIn(userReviews);

            if(reviews.isEmpty()) // edge case
                continue;

            BulkOperations bulkOps = mongoTemplate.bulkOps(
                    BulkOperations.BulkMode.UNORDERED,
                    Game.class
            );

            for (ReviewScoreDTO review : reviews) {
                Query query = new Query(Criteria.where("_id").is(review.getGameId()));

                Update update = new Update()
                        .pull("recentReviews", new Document("_id", new ObjectId(review.getId())))
                        .pull("allReviews", new ObjectId(review.getId()))
                        .inc("sumScore", -review.getScore())
                        .inc("countScore", -1);

                bulkOps.updateOne(query, update);
            }

            BulkWriteResult result = bulkOps.execute();
            modified += result.getModifiedCount();

        }

        return modified;
    }

    public long adjustGameStatsAndRemoveUserNode(String userId){
        long modified = 0;

        List<LibraryGameDTO> targetLibrary = userNeo4jRepository.deleteUserAndRetrieveLibrary(userId);

        if(!targetLibrary.isEmpty()){

            BulkOperations bulkOps = mongoTemplate.bulkOps(
                    BulkOperations.BulkMode.UNORDERED,
                    Game.class
            );

            for (LibraryGameDTO game : targetLibrary){
                Query query = new Query(Criteria.where("_id").is(game.getId()));

                Update update = new Update().inc("totalHoursPlayed", -game.getHoursPlayed())
                        .inc("numPlayers", -1);

                bulkOps.updateOne(query, update);
            }

            BulkWriteResult result = bulkOps.execute();
            modified += result.getModifiedCount();
        }
        return  modified;
    }

}
