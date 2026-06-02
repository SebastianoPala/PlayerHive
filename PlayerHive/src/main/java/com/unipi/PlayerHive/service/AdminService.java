package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.games.AddGameDTO;
import com.unipi.PlayerHive.DTO.games.EditGameDTO;
import com.unipi.PlayerHive.config.Exceptions.ResourceAlreadyExistsException;
import com.unipi.PlayerHive.model.game.Game;
import com.unipi.PlayerHive.model.game.GameNeo4j;
import com.unipi.PlayerHive.repository.ReviewRepository;
import com.unipi.PlayerHive.repository.games.GameNeo4jRepository;
import com.unipi.PlayerHive.repository.games.GameRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import com.unipi.PlayerHive.utility.batch.UserConsistencyManager;
import com.unipi.PlayerHive.utility.map.GameMapper;
import jakarta.annotation.Nonnull;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.beans.FeatureDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

@Service
public class AdminService {
    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final GameMapper gameMapper;

    private final UserRepository userRepository;

    private final ReviewRepository reviewRepository;

    private final UserConsistencyManager userConsistencyManager;

    public AdminService(GameRepository gameRepository, GameNeo4jRepository gameNeo4jRepository, GameMapper gameMapper, UserRepository userRepository, ReviewRepository reviewRepository,
                        UserConsistencyManager userConsistencyManager) {
        this.gameRepository = gameRepository;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.gameMapper = gameMapper;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.userConsistencyManager = userConsistencyManager;
    }

    // This function copies all the non-null fields from source to target, and only matches fields with the same name
    public static void copyNonNullProperties(Object source, Object target) {
        BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
    }

    private static String[] getNullPropertyNames (Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        return Stream.of(src.getPropertyDescriptors())
                .map(FeatureDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    double roundNumber(double num){
        return ((double) Math.round(num * 100)) / 100;
    }

    double calculateFinalPrice(double price, double discount){
        return price - (price * discount/100);
    }

    void fixPrices(Game game){
        game.setPrice(roundNumber(game.getPrice()));

        double finalPrice = roundNumber(calculateFinalPrice(game.getPrice(), game.getDiscount()));

        game.setFinalPrice(finalPrice);
    }

    @Transactional
    public void addGame(@Nonnull @Valid @RequestBody AddGameDTO newGame) {

        if(gameRepository.existsByName(newGame.getName()))
                throw new ResourceAlreadyExistsException("Game "+ newGame.getName() +" already exists");

        Game game = gameMapper.editGameDTOtoGame(newGame);

        fixPrices(game); // we round the price and calculate the final price

        game.setAllReviews(new ArrayList<>());
        game.setRecentReviews(new ArrayList<>());
        game.setTotalHoursPlayed((float) 0);

        game.setNumPlayers(0);
        game.setSumScore((float) 0);
        game.setCountScore(0);

        Game addedGame = gameRepository.save(game); // I need the game ID from MongoDb for Neo4j

        GameNeo4j gameN4j= new GameNeo4j(addedGame.getId(), game.getName(),game.getAchievements(),game.getImageURL());

        gameNeo4jRepository.save(gameN4j);
    }

    @Transactional
    public void editGame(String gameId, @Nonnull @Valid @RequestBody EditGameDTO editGame) {

        Game game = gameRepository.findById(gameId).orElseThrow(() -> new NoSuchElementException("The Game with id:\"" + gameId + "\" does not exist"));

        boolean updateReviewInfo = false;
        String gameName = game.getName();
        String gameImg = game.getImageURL();

        if(!gameName.equals(editGame.getName())){
            if (gameRepository.existsByName(editGame.getName())) { // avoids throwing an exception if I modify the game name to itself
                throw new ResourceAlreadyExistsException("Game "+ editGame.getName() +" already exists");
            }else{
                updateReviewInfo = true;
                gameName = editGame.getName();
            }
        }

        if(gameImg != null && !gameImg.equals(editGame.getImageURL()) ||
                        gameImg == null && editGame.getImageURL() != null){

            updateReviewInfo = true;
            gameImg = editGame.getImageURL();
        }

        if(updateReviewInfo && !game.getAllReviews().isEmpty()){
            List<String> reviews = game.getAllReviews().stream().map(ObjectId::toString).toList();
            long modified = reviewRepository.editInfoIn(reviews, gameName, gameImg);
            System.out.println(modified + " reviews had their info updated");
        }

        copyNonNullProperties(editGame,game);

        fixPrices(game); // we round the price and calculate the final price

        gameRepository.save(game);
        GameNeo4j gameNeo = gameNeo4jRepository.findById(gameId).orElseThrow(() -> new NoSuchElementException("Game not found on Neo4j"));

        copyNonNullProperties(editGame,gameNeo);

        gameNeo4jRepository.save(gameNeo);
    }

    @Transactional
    public void deleteGame(String gameId) {

        if(!gameRepository.existsById(gameId))
            throw new NoSuchElementException("The game chosen for deletion does not exist");

        System.out.println("A game with Id: " + gameId + " has been scheduled for deletion");

        //todo: this operation is super heavy, but games are basically never deleted, especially popular ones
        //todo: if we do not perform the user update here, it makes the user related queries heavier
        //all users "hoursPlayed" and "numGames" are decreased accordingly
        long modified = userConsistencyManager.adjustUserStatsAfterRemovalOf(gameId);
        System.out.println(modified + " users had their stats updated");

        ObjectId gameIdObj = new ObjectId(gameId);

        //all reviews are now deleted
        //long deleted = reviewRepository.removeByGameId(gameIdObj);

        List<String> reviewIds = gameRepository.getAllGameReviews(gameId).getReviews().stream().map(ObjectId::toString).toList();
        long deleted = reviewRepository.removeByIdIn(reviewIds);


        System.out.println(deleted + " reviews were deleted from the Review Collection");

        //the game node in neo4j is removed
        gameNeo4jRepository.deleteById(gameId);

        // all reviews of the game are removed from the reviews array present in every user document
        deleted = userRepository.removeAllGameReviewsFromUsers(gameIdObj); // todo bruh

        System.out.println(deleted + " users had their reviews updated");

        //we can finally delete the JSON document
        gameRepository.deleteById(gameId);
    }

}
