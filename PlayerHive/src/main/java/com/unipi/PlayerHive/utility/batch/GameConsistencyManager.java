package com.unipi.PlayerHive.utility.batch;

import com.mongodb.bulk.BulkWriteResult;
import com.unipi.PlayerHive.DTO.games.LibraryGameLightDTO;
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

/**
 * Component responsible for managing data consistency across databases concerning games
 * when user nodes are deleted or user-specific bulk operations are executed.
 */
@Component
public class GameConsistencyManager {

    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final ReviewRepository reviewRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Constructs a GameConsistencyManager with the required repositories.
     *
     * @param userRepository      The MongoDB repository for users.
     * @param userNeo4jRepository The Neo4j repository for user relationships.
     * @param reviewRepository    The MongoDB repository for reviews.
     * @param mongoTemplate       The Spring Data MongoTemplate for bulk operations.
     */
    public GameConsistencyManager(UserRepository userRepository, UserNeo4jRepository userNeo4jRepository, ReviewRepository reviewRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.userNeo4jRepository = userNeo4jRepository;
        this.reviewRepository = reviewRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Removes all reviews created by a specific user from the associated game documents,
     * decrementing the sum score and review counters accordingly in batches.
     *
     * @param userId The ID of the user whose reviews are being removed.
     * @return The total number of game documents successfully modified.
     */
    public long removeUserReviewsFromGames(String userId){
        boolean reviews_left = true;
        int page_size = 1000;
        int step = 0;

        long modified = 0;
        long deleted = 0;
        while(reviews_left){
            // user reviews are retrieved and the review id is extracted from each
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

            // every game has to be updated, the operation is the same for all games, but
            // with different values for each
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

            deleted = reviewRepository.removeByIdIn(userReviews);

        }
        System.out.println(deleted + " reviews were deleted from the Review Collection");

        return modified;
    }

    /**
     * Adjusts the overall statistics (total hours played and number of players) for all games
     * a user played, immediately following the removal of the user's node from Neo4j.
     *
     * @param userId The ID of the deleted user.
     * @return The total number of game documents successfully modified.
     */
    public long adjustGameStatsAndRemoveUserNode(String userId){
        long modified = 0;
        List<LibraryGameLightDTO> targetLibrary = userNeo4jRepository.deleteUserAndRetrieveLibrary(userId);

        if(!targetLibrary.isEmpty()){

            BulkOperations bulkOps = mongoTemplate.bulkOps(
                    BulkOperations.BulkMode.UNORDERED,
                    Game.class
            );

            // we have to perform the same operation to every game present in the user library,
            // but with different values for each
            for (LibraryGameLightDTO game : targetLibrary){
                Query query = new Query(Criteria.where("_id").is(game.getId()));
                Update update = new Update().inc("totalHoursPlayed", -game.getHoursPlayed())
                        .inc("numPlayers", -1);
                bulkOps.updateOne(query, update);
            }

            BulkWriteResult result = bulkOps.execute();
            modified += result.getModifiedCount();
        }
        return modified;
    }

}