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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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

    // JwtFilter already put the authenticated user in the security context earlier in the request, this just reads it back out
    private User getAuthenticatedUser() {
        return ((UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal())
                .getUser();
    }

    public GameInfoDTO getGameById(String gameId) {
        Game game = gameRepository.findByIdLight(gameId).orElseThrow(() -> new NoSuchElementException("Game not found"));

        GameInfoDTO gameInfo = gameMapper.gameToGameInfoDTO(game);

        Float userScore = (game.getCountScore() > 0) ? game.getSumScore() / game.getCountScore() : null;
        gameInfo.setUserScore(userScore);

        Float avgPlay = (game.getNumPlayers() > 0) ? game.getTotalHoursPlayed() / game.getNumPlayers() : 0;
        gameInfo.setAveragePlaytime(avgPlay);

        return gameInfo;
    }

    public GameSearchContainerDTO searchGameByName(String gameName, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);

        Slice<GameSearchDTO> result = gameRepository.searchByNameContaining(gameName, pageable);

        return new GameSearchContainerDTO(result.getContent(),result.isLast());
    }

    // todo TEST
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

    @Transactional
    public void deleteReview(String reviewId) {

        // only the review author or an admin can delete the requested review, anyone else gets rejected :/
        User requestingUser = getAuthenticatedUser();
        Review deletedReview;

        boolean isAdmin = requestingUser.getRole().equalsIgnoreCase("ADMIN");

        if(isAdmin)
            deletedReview = reviewRepository.removeById(reviewId);
        else
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

    public List<GameStatsDTO> getDeals(int minReviews, double minPrice, double maxPrice, double minRating){
        return gameRepository.getQualityToPriceGames(minReviews, minPrice, maxPrice, minRating);
    }

    public List<GameInvestmentDTO> getInvestments(int minPlayers, double minPrice, double maxPrice, double minAvgTime){
        return gameRepository.getTimeToPriceGames(minPlayers, minPrice, maxPrice, minAvgTime);
    }

    public List<GameStatsDTO> getDiscussed(){
        return gameRepository.findMostDiscussedGames();
    }

    public List<GameStatsDTO> getTopGames(int minReviews){
        return gameRepository.getTopRatedGames(minReviews);
    }

    public List<GameRecommendationDTO> getRecommendations(){
        return gameNeo4jRepository.getGameRecommendations(getAuthenticatedUser().getId(),10);
    }

    public List<TrendingGameDTO> getTrendingGames(){
        return gameNeo4jRepository.getTrendingGamesAmongFriends(100);
    }

    public List<HiddenGemDTO> getHiddenGems(int nicheThreshold){

        return gameNeo4jRepository.getHiddenGems(getAuthenticatedUser().getId(),nicheThreshold);
    }

    public List<FriendMagnetDTO> getRelatedGames(String gameId, int limit) {
        return gameNeo4jRepository.getRelatedGames(gameId, limit);
    }

    // admin analytics

    public List<GenreStatsDTO> getGenreStats(){
        return gameRepository.getGenreStats();
    }

    public List<OsPlatformStatsDTO> getOsPlatformStats(){
        return gameRepository.getOsPlatformStats();
    }

    public List<ReleaseYearStatsDTO> getReleaseYearStats(){
        return gameRepository.getReleaseYearStats();
    }

}
