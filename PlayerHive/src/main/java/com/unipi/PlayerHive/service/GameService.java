package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.analytics.GenreStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.OsPlatformStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.ReleaseYearStatsDTO;
import com.unipi.PlayerHive.DTO.containers.GameReviewContainerDTO;
import com.unipi.PlayerHive.DTO.containers.ReviewIdContainerDTO;
import com.unipi.PlayerHive.DTO.games.*;
import com.unipi.PlayerHive.DTO.reviews.*;
import com.unipi.PlayerHive.config.Exceptions.ResourceAlreadyExistsException;
import com.unipi.PlayerHive.model.Review;
import com.unipi.PlayerHive.model.game.Game;
import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.repository.ReviewRepository;
import com.unipi.PlayerHive.repository.games.GameNeo4jRepository;
import com.unipi.PlayerHive.repository.games.GameRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import com.unipi.PlayerHive.utility.map.GameMapper;
import com.unipi.PlayerHive.utility.map.ReviewMapper;
import jakarta.transaction.Transactional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service class handling game retrieval, search, reviews, and analytical game queries.
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final GameMapper gameMapper;
    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final UserRepository userRepository;

    public GameService(GameRepository gameRepository, GameNeo4jRepository gameNeo4jRepository,
                       GameMapper gameMapper, ReviewRepository reviewRepository, ReviewMapper reviewMapper, UserRepository userRepository
    ){
        this.gameRepository = gameRepository;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.gameMapper = gameMapper;
        this.reviewRepository = reviewRepository;
        this.reviewMapper = reviewMapper;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves the currently authenticated user from the Spring Security context.
     *
     * @return The authenticated User entity.
     */
    // JwtFilter already put the authenticated user in the security context earlier in the request, this just reads it back out
    private User getAuthenticatedUser() {
        return ((UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal())
                .getUser();
    }

    /**
     * Retrieves detailed information about a specific game, computing its average score and playtime.
     *
     * @param gameId The ID of the game.
     * @return A GameInfoDTO containing the game data.
     * @throws NoSuchElementException if the game does not exist.
     */
    public GameInfoDTO getGameById(String gameId) {
        Game game = gameRepository.findByIdLight(gameId).orElseThrow(() -> new NoSuchElementException("Game not found"));

        GameInfoDTO gameInfo = gameMapper.gameToGameInfoDTO(game);

        Float userScore = (game.getCountScore() > 0) ? game.getSumScore() / game.getCountScore() : null;
        gameInfo.setUserScore(userScore);

        Float avgPlay = (game.getNumPlayers() > 0) ? game.getTotalHoursPlayed() / game.getNumPlayers() : 0;
        gameInfo.setAveragePlaytime(avgPlay);

        return gameInfo;
    }

    /**
     * Searches for games by name utilizing a case-insensitive match.
     *
     * @param gameName The query string to search for.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container with the paginated game results.
     */
    public GameSearchContainerDTO searchGameByName(String gameName, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);

        Slice<GameSearchDTO> result = gameRepository.searchByNameContaining(gameName, pageable);

        return new GameSearchContainerDTO(result.getContent(),result.isLast());
    }

    /**
     * Retrieves the paginated reviews for a specific game.
     *
     * @param gameId The ID of the game.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container with the game's reviews.
     * @throws NoSuchElementException if the game does not exist.
     */
    public GameReviewContainerDTO getGameReviews(String gameId, int page, int size) {

        if(!gameRepository.existsById(gameId))
            throw new NoSuchElementException("The game does not exist");

        int skip = page*size;

        ReviewIdContainerDTO reviewContainer = gameRepository.getGameReviews(gameId, skip, size);

        List<String> reviewIds =  reviewContainer.getReviews()
                .stream().map(ObjectId::toString).toList();

        List<GameReviewDTO> reviews = reviewRepository.findGameReviewsByIdIn(reviewIds);

        int totalReviews = reviewContainer.getCountScore();

        int numPages = (totalReviews / size) + ((totalReviews % size > 0) ? 1 : 0);
        boolean isLastPage = (totalReviews - skip <= size);


        return new GameReviewContainerDTO(reviews,numPages,isLastPage);
    }

    /**
     * Adds a review for a game, updating the game statistics and the user's review history.
     *
     * @param gameId The ID of the game to review.
     * @param addReviewDTO The text and score of the review.
     * @throws ResourceAlreadyExistsException if the user has already reviewed the game.
     * @throws NoSuchElementException if the game does not exist.
     * @throws RuntimeException if the database update fails.
     */
    @Transactional
    public void addReview(String gameId, AddReviewDTO addReviewDTO) {

        User user = getAuthenticatedUser();
        String userId = user.getId();

        ObjectId userIdObj = new ObjectId(userId);
        ObjectId gameIdObj = new ObjectId(gameId);

        if(userRepository.hasUserAlreadyReviewed(userId, gameIdObj)){
            throw new ResourceAlreadyExistsException("The user already reviewed this game");
        }

        GameNameImageDTO gameNameImage =  gameRepository.getGameNameAndImageById(gameId)
                .orElseThrow(() -> new NoSuchElementException("the specified game does not exist"));

        Review review = new Review(null,new ObjectId(gameId),userIdObj,user.getUsername(),user.getPfpURL(),gameNameImage.getGameName(),
                gameNameImage.getGameImage(), addReviewDTO.getReviewText(), addReviewDTO.getScore(), LocalDateTime.now());

        // the review is saved in the Review collection ...
        Review savedReview = reviewRepository.save(review);

        GameReviewDTO recentReview = reviewMapper.reviewToRecentReviewDTO(savedReview);

        // ... in the recentReviews array (ready for retrieval), and in the allReviews array (as a lightweight version)
        int modified = gameRepository.addReviewToGame(gameId,new ObjectId(recentReview.getId()) , recentReview, addReviewDTO.getScore());
        if(modified != 1)
            throw new RuntimeException("An error has occurred when adding the review to the game");

        // the review id and the game id are added to the user document as well
        OldUserReviewDTO userReview = new OldUserReviewDTO(new ObjectId(savedReview.getId()), new ObjectId(gameId));

        userRepository.addReviewToUser(userId, userReview);
    }

    /**
     * Deletes a review from the system. Reverses statistical changes in the game document
     * and removes the entry from the author's history.
     *
     * @param reviewId The ID of the review to delete.
     * @throws IllegalArgumentException if a standard user tries to delete another user's review.
     * @throws NoSuchElementException if the review is not found.
     * @throws RuntimeException if database consistency checks fail.
     */
    @Transactional
    public void deleteReview(String reviewId) {

        // only the review author or an admin can delete the requested review, anyone else gets rejected :/
        User requestingUser = getAuthenticatedUser();
        Review deletedReview;

        boolean isAdmin = requestingUser.getRole().equalsIgnoreCase("ADMIN");

        if(isAdmin)
            deletedReview = reviewRepository.removeById(reviewId);

        else // ownership check
            deletedReview = reviewRepository.removeByIdAndUserId(reviewId,new ObjectId(requestingUser.getId()));

        if (deletedReview == null) {
            if(!isAdmin)
                throw new IllegalArgumentException("No user reviews match the requested id");
            else
                throw new NoSuchElementException("The review does not exist");
        }

        // the review is deleted from both the game's review arrays
        int modified = gameRepository.deleteReviewFromGame(deletedReview.getGameId(),deletedReview.getId(),-deletedReview.getScore());
        if(modified != 1)
            throw new RuntimeException("The server couldn't delete the review due to inconsistencies");

        // clean the entry out of the user's reviewIds array too
        userRepository.removeReviewFromUser(deletedReview.getUserId().toString(), new ObjectId(reviewId));
    }

    // INTERESTING QUERIES ====================

    /**
     * Retrieves games offering high quality ratings relative to their price.
     *
     * @param minReviews Minimum reviews necessary.
     * @param minPrice Minimum price threshold.
     * @param maxPrice Maximum price threshold.
     * @param minRating Minimum rating necessary.
     * @return List of matching GameStatsDTOs.
     */
    public List<GameStatsDTO> getDeals(int minReviews, double minPrice, double maxPrice, double minRating){
        return gameRepository.getQualityToPriceGames(minReviews, minPrice, maxPrice, minRating);
    }

    /**
     * Retrieves games offering significant average playtime relative to their price.
     *
     * @param minPlayers Minimum players necessary.
     * @param minPrice Minimum price threshold.
     * @param maxPrice Maximum price threshold.
     * @param minAvgTime Minimum average playtime needed.
     * @return List of matching GameInvestmentDTOs.
     */
    public List<GameInvestmentDTO> getInvestments(int minPlayers, double minPrice, double maxPrice, double minAvgTime){
        return gameRepository.getTimeToPriceGames(minPlayers, minPrice, maxPrice, minAvgTime);
    }

    /**
     * Retrieves the most discussed games based on review density and timing.
     *
     * @return List of GameStatsDTOs.
     */
    public List<GameStatsDTO> getDiscussed(){
        return gameRepository.findMostDiscussedGames();
    }

    /**
     * Retrieves the overall top-rated games that meet the review threshold.
     *
     * @param minReviews Minimum reviews necessary.
     * @return List of top GameStatsDTOs.
     */
    public List<GameStatsDTO> getTopGames(int minReviews){
        return gameRepository.getTopRatedGames(minReviews);
    }

    /**
     * Retrieves the newest released games for the home page.
     * Lightweight: backed by a descending index on release_date, so it avoids
     * the full-catalogue scan and sort performed by getTopGames.
     *
     * @return List of the 15 most recently released games.
     */
    public List<NewGameInfoDTO> getNewReleases(){
        return gameRepository.getNewReleases(PageRequest.of(0, 15));
    }

    /**
     * Generates item-based game recommendations for the authenticated user.
     *
     * @return List of recommended games based on friend activity.
     */
    public List<GameRecommendationDTO> getRecommendations(){
        return gameNeo4jRepository.getGameRecommendations(getAuthenticatedUser().getId(),10);
    }

    /**
     * Retrieves games currently trending heavily among friend networks.
     *
     * @param limit Max items to return.
     * @param minSocial Minimum social count needed.
     * @return List of TrendingGameDTOs.
     */
    public List<TrendingGameDTO> getTrendingGames(int limit, int minSocial){
        return gameNeo4jRepository.getTrendingGamesAmongFriends(limit, minSocial);
    }

    /**
     * Finds hidden gems (games popular with friends but niche globally) for the authenticated user.
     *
     * @param nicheThreshold Global popularity threshold limits.
     * @return List of HiddenGemDTOs.
     */
    public List<HiddenGemDTO> getHiddenGems(int nicheThreshold){

        return gameNeo4jRepository.getHiddenGems(getAuthenticatedUser().getId(),nicheThreshold);
    }

    /**
     * Retrieves related games based on overlapping player bases.
     *
     * @param gameId The source game ID.
     * @param minShared Minimum shared players to form a link.
     * @param limit Max items to return.
     * @return List of RelatedGameDTOs.
     * @throws NoSuchElementException if the source game does not exist.
     */
    public List<RelatedGameDTO> getRelatedGames(String gameId,int minShared, int limit) {
        if(!gameRepository.existsById(gameId))
            throw new NoSuchElementException("The requested game does not exist");

        return gameNeo4jRepository.getRelatedGames(gameId, minShared, limit);
    }

    // admin analytics

    /**
     * Administrative query to fetch average stats grouped by game genre.
     *
     * @return List of GenreStatsDTOs.
     */
    public List<GenreStatsDTO> getGenreStats(){
        return gameRepository.getGenreStats();
    }

    /**
     * Administrative query to fetch average stats grouped by the number of supported OS platforms.
     *
     * @return List of OsPlatformStatsDTOs.
     */
    public List<OsPlatformStatsDTO> getOsPlatformStats(){
        return gameRepository.getOsPlatformStats();
    }

    /**
     * Administrative query to fetch average game scores grouped by release year.
     *
     * @return List of ReleaseYearStatsDTOs.
     */
    public List<ReleaseYearStatsDTO> getReleaseYearStats(){
        return gameRepository.getReleaseYearStats();
    }

}