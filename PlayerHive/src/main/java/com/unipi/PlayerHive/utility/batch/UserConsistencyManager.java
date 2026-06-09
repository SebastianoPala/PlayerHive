package com.unipi.PlayerHive.utility.batch;

import com.mongodb.bulk.BulkWriteResult;
import com.unipi.PlayerHive.DTO.users.GameOwnerDTO;
import com.unipi.PlayerHive.model.Review;
import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.repository.games.GameNeo4jRepository;
import com.unipi.PlayerHive.repository.games.GameRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Component responsible for managing and maintaining data consistency across databases
 * concerning users when bulk operations or deletions occur (e.g., removing a game).
 */
@Component
public class UserConsistencyManager {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Constructs a UserConsistencyManager with the required repositories.
     *
     * @param userRepository      The MongoDB repository for users.
     * @param gameRepository      The MongoDB repository for games.
     * @param gameNeo4jRepository The Neo4j repository for game relationships.
     * @param mongoTemplate       The Spring Data MongoTemplate for bulk operations.
     */
    public UserConsistencyManager(UserRepository userRepository, GameRepository gameRepository, GameNeo4jRepository gameNeo4jRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Adjusts the overall game statistics (hours played, number of games) for all users
     * who owned a specific game after that game has been removed.
     * Processes deletions in batches to avoid memory overload.
     *
     * @param gameId The ID of the game that was removed.
     * @return The total number of users modified.
     */
    public long adjustUserStatsAfterRemovalOf(String gameId) {

        int batchSize = 10000;
        long modified = 0;
        boolean relationshipsLeft = true;

        while (relationshipsLeft) {

            List<GameOwnerDTO> owners = gameNeo4jRepository.deletePlayedEdgesInBatch(gameId,batchSize);
            if(owners.isEmpty())
                break;
            else if(owners.size() < batchSize)
                relationshipsLeft = false;

            modified += batchDecreaseUserGameStats(owners);
        }

        return modified;
    }

    /**
     * Helper method to execute an unordered bulk update reducing hours played and
     * the number of owned games for a batch of users.
     *
     * @param batch A list of GameOwnerDTOs containing the users and the playtime to subtract.
     * @return The number of user documents successfully modified.
     */
    private long batchDecreaseUserGameStats(List<GameOwnerDTO> batch) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, User.class);

        // the operation we have to perform on every user is the same, but we have different values
        // for each
        for (GameOwnerDTO dto : batch) {
            Query query = new Query(Criteria.where("_id").is(dto.getId()));
            Update update = new Update()
                    .inc("hoursPlayed", -dto.getHoursPlayed())
                    .inc("numGames", -1);
            bulkOps.updateOne(query, update);
        }

        BulkWriteResult result = bulkOps.execute();

        return result.getModifiedCount();
    }

    /**
     * Removes all reviews associated with a specific game from the database and updates
     * the review lists within the respective user documents in batches.
     *
     * @param gameId The ID of the game whose reviews must be removed.
     */
    public void removeAllGameReviews(String gameId){
        int page_size = 10000;
        int skip = 0;
        boolean reviews_left = true;

        long deleted_reviews = 0;
        long updated_users = 0;
        ObjectId gameIdObj = new ObjectId(gameId);

        while(reviews_left){
            List<String> reviewIds = gameRepository.getGameReviews(gameId,skip,page_size).getReviews().stream().map(ObjectId::toString).toList();
            if(reviewIds.isEmpty())
                break;

            if(reviewIds.size() < page_size){
                reviews_left = false;
            }

            Query query = new Query(Criteria.where("_id").in(reviewIds));
            // removes the documents from the DB and returns a list of the removed entity
            List<String> userIds = mongoTemplate.findAllAndRemove(query, Review.class).stream().map( review -> review.getUserId().toString()).toList();
            deleted_reviews += userIds.size();

            updated_users += userRepository.removeReviewFromUsersByGame(userIds, gameIdObj);

            skip += page_size;
        }

        System.out.println(deleted_reviews + " reviews have been deleted from the Reviews collection");
        System.out.println(updated_users + " users had their reviews updated");
    }

}