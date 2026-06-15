package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.games.AddGameDTO;
import com.unipi.PlayerHive.DTO.games.EditGameDTO;
import com.unipi.PlayerHive.config.Exceptions.ResourceAlreadyExistsException;
import com.unipi.PlayerHive.model.game.Game;
import com.unipi.PlayerHive.model.game.GameNeo4j;
import com.unipi.PlayerHive.repository.ReviewRepository;
import com.unipi.PlayerHive.repository.games.GameNeo4jRepository;
import com.unipi.PlayerHive.repository.games.GameRepository;
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

/**
 * Service class handling administrative logic such as adding, editing, and deleting games.
 */
@Service
public class AdminService {
    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final GameMapper gameMapper;

    private final ReviewRepository reviewRepository;

    private final UserConsistencyManager userConsistencyManager;

    public AdminService(GameRepository gameRepository, GameNeo4jRepository gameNeo4jRepository, GameMapper gameMapper, ReviewRepository reviewRepository,
                        UserConsistencyManager userConsistencyManager) {
        this.gameRepository = gameRepository;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.gameMapper = gameMapper;
        this.reviewRepository = reviewRepository;
        this.userConsistencyManager = userConsistencyManager;
    }

    /**
     * Copies non-null properties from a source object to a target object.
     *
     * @param source The object containing new field values.
     * @param target The entity object to be updated.
     */
    // This function copies all the non-null fields from source to target, and only matches fields with the same name
    public static void copyNonNullProperties(Object source, Object target) {
        BeanUtils.copyProperties(source, target, getNullPropertyNames(source));
    }

    /**
     * Identifies fields within the source object that are currently null.
     *
     * @param source The object to inspect.
     * @return Array of property names that hold null values.
     */
    private static String[] getNullPropertyNames (Object source) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        return Stream.of(src.getPropertyDescriptors())
                .map(FeatureDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    /**
     * Utility method to round doubles to 2 decimal places.
     *
     * @param num The double to round.
     * @return The rounded double.
     */
    private double roundNumber(double num){
        return ((double) Math.round(num * 100)) / 100;
    }

    /**
     * Calculates the final price of a game based on the discount percentage.
     *
     * @param price Original price.
     * @param discount Discount percentage.
     * @return The calculated final price.
     */
    private double calculateFinalPrice(double price, double discount){
        return price - (price * discount/100);
    }

    /**
     * Re-calculates and rounds the prices attached to a Game entity.
     *
     * @param game The game to calculate prices for.
     */
    private void fixPrices(Game game){
        game.setPrice(roundNumber(game.getPrice()));

        double finalPrice = roundNumber(calculateFinalPrice(game.getPrice(), game.getDiscount()));

        game.setFinalPrice(finalPrice);
    }

    /**
     * Adds a newly registered game into MongoDB and its corresponding node into Neo4j.
     * Initializes default statistics.
     *
     * @param newGame The AddGameDTO containing game creation fields.
     * @throws ResourceAlreadyExistsException if a game with the same name already exists.
     */
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

        Game addedGame = gameRepository.save(game); //the game ID for Neo4j is obtained from MongoDB

        GameNeo4j gameN4j= new GameNeo4j(addedGame.getId(), game.getName(),game.getAchievements(),game.getImageURL());

        gameNeo4jRepository.save(gameN4j);
    }

    /**
     * Updates an existing game with new information. Will trigger bulk updates
     * on reviews if the game's name or image URL changes.
     *
     * @param gameId The ID of the game to edit.
     * @param editGame The fields intended for updating.
     * @throws ResourceAlreadyExistsException if editing results in a duplicate name conflict.
     * @throws NoSuchElementException if the game is missing.
     */
    @Transactional
    public void editGame(String gameId, @Nonnull @Valid @RequestBody EditGameDTO editGame) {

        Game game = gameRepository.findById(gameId).orElseThrow(() -> new NoSuchElementException("The Game with id:\"" + gameId + "\" does not exist"));

        boolean updateReviewInfo = false; // all reviews contain the game name and game image, hence we have to keep them consistent
        String gameName = game.getName();
        String gameImg = game.getImageURL();

        if(!gameName.equals(editGame.getName())){ // avoids throwing an exception if I modify the game name to itself
            if (gameRepository.existsByName(editGame.getName())) {
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

    /**
     * Permanently deletes a game and handles the heavy cleanup tasks for adjusting users'
     * total played stats and cascade-deleting all relevant reviews.
     *
     * @param gameId The ID of the game to be deleted.
     * @throws NoSuchElementException if the game does not exist.
     */
    @Transactional
    public void deleteGame(String gameId) {

        if(!gameRepository.existsById(gameId))
            throw new NoSuchElementException("The game chosen for deletion does not exist");

        System.out.println("A game with Id: " + gameId + " has been scheduled for deletion");

        //all users "hoursPlayed" and "numGames" are decreased accordingly
        long modified = userConsistencyManager.adjustUserStatsAfterRemovalOf(gameId);
        System.out.println(modified + " users had their stats updated");

        userConsistencyManager.removeAllGameReviews(gameId);

        //the game node in neo4j is removed
        gameNeo4jRepository.deleteById(gameId);

        //we can finally delete the JSON document
        gameRepository.deleteById(gameId);
    }

}