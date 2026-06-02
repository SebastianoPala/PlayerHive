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

@Component
public class UserConsistencyManager {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final MongoTemplate mongoTemplate;

    public UserConsistencyManager(UserRepository userRepository, GameRepository gameRepository, GameNeo4jRepository gameNeo4jRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.mongoTemplate = mongoTemplate;
    }

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

    private long batchDecreaseUserGameStats(List<GameOwnerDTO> batch) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, User.class);

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

            // Rimuove i documenti dal DB e restituisce la lista delle entità rimosse
            List<String> userIds = mongoTemplate.findAllAndRemove(query, Review.class).stream().map( review -> review.getUserId().toString()).toList();

            deleted_reviews += userIds.size();

            updated_users += userRepository.removeReviewFromUsersByGame(userIds, gameIdObj);

            skip += page_size;

        }

        System.out.println(deleted_reviews + " reviews have been deleted from the Reviews collection");
        System.out.println(updated_users + " users had their reviews updated");
    }

}
