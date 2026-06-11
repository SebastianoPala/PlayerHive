package com.unipi.PlayerHive.controller;

import com.unipi.PlayerHive.DTO.games.*;
import com.unipi.PlayerHive.DTO.reviews.AddReviewDTO;
import com.unipi.PlayerHive.DTO.containers.GameReviewContainerDTO;
import com.unipi.PlayerHive.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for game catalog management, searching, review handling, and analytical game queries.
 */
@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Game search, reviews, and advanced queries")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService){
        this.gameService = gameService;
    }

    /**
     * Retrieves comprehensive information for a specific game.
     *
     * @param gameId The ID of the game.
     * @return ResponseEntity containing the game information.
     */
    @GetMapping("/{gameId}")
    @Operation(summary = "Get game info", description = "Returns full details, user score, and average playtime.")
    @ApiResponse(responseCode = "200", description = "Game details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<GameInfoDTO> getInfo(@PathVariable @NotNull  @Size(min = 24, max = 24) String gameId){
        return ResponseEntity.ok(gameService.getGameById(gameId));
    }

    /**
     * Conducts a text search through the game catalog based on a provided query string.
     *
     * @param gameName The partial or full name of the game to search for.
     * @param page     The page number for pagination.
     * @param size     The number of results per page.
     * @return ResponseEntity containing paginated game search results.
     */
    @GetMapping("/search/{gameName}")
    @Operation(summary = "Search games by name", description = "Paginated text search within the catalog.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<GameSearchContainerDTO> searchByName(@PathVariable String gameName,
                                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                                               @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size){
        return ResponseEntity.ok(gameService.searchGameByName(gameName,page,size));
    }

    /**
     * Retrieves paginated user reviews for a specific game.
     *
     * @param gameId The ID of the game.
     * @param page   The page number for pagination.
     * @param size   The number of reviews per page.
     * @return ResponseEntity containing the game's reviews.
     */
    @GetMapping("/reviews/{gameId}")
    @Operation(summary = "Show reviews", description = "Returns a paginated list of reviews associated with a specific game.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<GameReviewContainerDTO> showGameReviews(@PathVariable @NotNull  @Size(min = 24, max = 24) String gameId,
                                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size){
        return ResponseEntity.ok(gameService.getGameReviews(gameId,page,size));
    }

    /**
     * Publishes a new user review for a specific game.
     *
     * @param gameId       The ID of the game being reviewed.
     * @param addReviewDTO The score and text content of the review.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/addReview/{gameId}")
    @Operation(summary = "Add a review", description = "Adds a review to a game. A user cannot review the same game twice.")
    @ApiResponse(responseCode = "200", description = "Review added successfully")
    @ApiResponse(responseCode = "409", description = "The user already reviewed this game")
    public ResponseEntity<String> addReview(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId, @Valid @RequestBody AddReviewDTO addReviewDTO){
        gameService.addReview(gameId, addReviewDTO);
        return ResponseEntity.ok("Review added successfully");
    }

    /**
     * Deletes a review. Can only be invoked by the original author or an Admin.
     *
     * @param reviewId The ID of the review to delete.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/deleteReview/{reviewId}")
    @Operation(summary = "Delete a review", description = "Removes a review. Can only be done by the author or an Admin.")
    @ApiResponse(responseCode = "200", description = "Review deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this review")
    public ResponseEntity<String> deleteReview(@PathVariable @NotNull  @Size(min = 24, max = 24) String reviewId){
        gameService.deleteReview(reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }

    // INTERESTING QUERIES ============================================

    /**
     * Fetches games filtered and sorted to highlight those providing the best value
     * based on their quality-to-price ratio.
     *
     * @param minReviews Minimum number of reviews to be considered.
     * @param minPrice   Minimum price filter.
     * @param maxPrice   Maximum price filter.
     * @param minRating  Minimum average rating filter.
     * @return ResponseEntity containing a list of the best deals.
     */
    @GetMapping("/getDeals")
    @Operation(
            summary = "Get best value games",
            description = "Returns games sorted by rating-to-price ratio. Filter by price range and minimum rating. Only games with at least minReviews reviews are included."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<GameStatsDTO>> getDeals(
            @RequestParam(defaultValue = "5") @Min(1) int minReviews,
            @RequestParam(defaultValue = "1") @Min(0) double minPrice,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) double maxPrice,
            @RequestParam(defaultValue = "1") @Min(1) @Max(10) double minRating
    ){
        return ResponseEntity.ok(gameService.getDeals(minReviews, minPrice, maxPrice, minRating));
    }

    /**
     * Fetches games filtered and sorted to highlight those providing the best
     * overall playtime relative to their cost.
     *
     * @param minPlayers Minimum active players required.
     * @param minPrice   Minimum price filter.
     * @param maxPrice   Maximum price filter.
     * @param minAvgTime Minimum average playtime filter.
     * @return ResponseEntity containing a list of top game investments.
     */
    @GetMapping("/getInvestments")
    @Operation(
            summary = "Get best time-value games",
            description = "Returns games sorted by average playtime-to-price ratio. Filter by price range and minimum average playtime."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<GameInvestmentDTO>> getInvestments(
            @RequestParam(defaultValue = "1") @Min(1) int minPlayers,
            @RequestParam(defaultValue = "1") @Min(0) double minPrice,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) double maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) double minAvgTime){
        return ResponseEntity.ok(gameService.getInvestments(minPlayers, minPrice, maxPrice, minAvgTime));
    }

    /**
     * Retrieves the games with the highest review density (most active recent discussions).
     *
     * @return ResponseEntity containing a list of highly discussed games.
     */
    @GetMapping("/getDiscussed")
    @Operation(
            summary = "Get most actively discussed games",
            description = "Returns games with the most review activity in the shortest time window, based on recent reviews."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<GameStatsDTO>> getDiscussed(){
        return ResponseEntity.ok(gameService.getDiscussed());
    }

    /**
     * Fetches the top-rated games globally, meeting the required review threshold.
     *
     * @param minReviews Minimum required reviews to qualify.
     * @return ResponseEntity containing the top-rated games.
     */
    @GetMapping("/getTopGames")
    @Operation(
            summary = "Get top rated games",
            description = "Returns the highest rated games. Only includes games with at least minReviews reviews to filter out games with a single inflated score."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<GameStatsDTO>> getTopGames(
            @RequestParam(defaultValue = "3") @Min(1) int minReviews
    ){
        return ResponseEntity.ok(gameService.getTopGames(minReviews));
    }

    /**
     * Retrieves the most recently released games, shown on the home page.
     * Lightweight index-backed query used instead of the heavier top-rated
     * aggregation so the landing page stays fast under load.
     *
     * @return ResponseEntity containing the newest released games.
     */
    @GetMapping("/getNewReleases")
    @Operation(
            summary = "Get newly released games",
            description = "Returns the 15 most recently released games. Backed by a descending index on release_date for fast home-page loading."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<NewGameInfoDTO>> getNewReleases(){
        return ResponseEntity.ok(gameService.getNewReleases());
    }

    /**
     * Generates a personalized list of game recommendations based on the activity
     * of the authenticated user's friends.
     *
     * @return ResponseEntity containing personalized game recommendations.
     */
    @GetMapping("/getRecommendations")
    @Operation(summary = "Get game recommendations", description = "Provides a list of game recommendations based on the user's friend network.")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully")
    public ResponseEntity<List<GameRecommendationDTO>> getRecommendations(){
        return ResponseEntity.ok(gameService.getRecommendations());
    }

    /**
     * Discovers "Hidden Gems" that are highly popular within the user's social circle
     * but lack significant global popularity.
     *
     * @param nicheThreshold The maximum global player count to classify as a hidden gem.
     * @return ResponseEntity containing hidden gem game recommendations.
     */
    @GetMapping("/getHiddenGems")
    @Operation(summary = "Get hidden gems", description = "Retrieves obscure games that are nonetheless popular among the user's friends.")
    @ApiResponse(responseCode = "200", description = "Hidden gems retrieved successfully")
    public ResponseEntity<List<HiddenGemDTO>> getHiddenGems(
            @RequestParam(defaultValue = "5") @Min(3) int nicheThreshold
    ){
        return ResponseEntity.ok(gameService.getHiddenGems(nicheThreshold));
    }

    /**
     * Suggests games related to a specified target game by analyzing the overlap of player bases.
     *
     * @param gameId    The target game ID to find relations for.
     * @param minShared Minimum number of shared players required to form a relationship link.
     * @param limit     Max number of related games to return.
     * @return ResponseEntity containing a list of related games.
     */
    @GetMapping("/{gameId}/getRelatedGames")
    @Operation(summary = "Related Games", description = "Games most co-played by players of this game — 'if you liked this, you'll like these'.")
    @ApiResponse(responseCode = "200", description = "List returned")
    public ResponseEntity<List<RelatedGameDTO>> getRelatedGames(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "1") @Min(1) int minShared,
            @RequestParam(defaultValue = "10") @Min(10) @Max(25) int limit) {

        return ResponseEntity.ok(gameService.getRelatedGames(gameId, minShared, limit));
    }
}