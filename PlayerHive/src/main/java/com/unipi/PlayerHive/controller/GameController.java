package com.unipi.PlayerHive.controller;

import com.unipi.PlayerHive.DTO.analytics.GenreStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.OsPlatformStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.ReleaseYearStatsDTO;
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

@RestController
@RequestMapping("/api/games")

// TODO FIX RANGES OF REQUESTS

@Tag(name = "3. Games", description = "Game search, reviews, and advanced queries")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService){
        this.gameService = gameService;
    }

    @GetMapping("/{gameId}")
    @Operation(summary = "Get game info", description = "Returns full details, user score, and average playtime.")
    @ApiResponse(responseCode = "200", description = "Game details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<GameInfoDTO> getInfo(@PathVariable @NotNull  @Size(min = 24, max = 24) String gameId){
        return ResponseEntity.ok(gameService.getGameById(gameId));
    }

    @GetMapping("/search/{gameName}")
    @Operation(summary = "Search games by name", description = "Paginated text search within the catalog.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<GameSearchContainerDTO> searchByName(@PathVariable String gameName,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size){
        return ResponseEntity.ok(gameService.searchGameByName(gameName,page,size));
    }

    @GetMapping("/reviews/{gameId}")
    @Operation(summary = "Show reviews", description = "Returns a paginated list of reviews associated with a specific game.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<GameReviewContainerDTO> showGameReviews(@PathVariable @NotNull  @Size(min = 24, max = 24) String gameId,
                                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size){
        return ResponseEntity.ok(gameService.getGameReviews(gameId,page,size));
    }

    @PostMapping("/addReview/{gameId}")
    @Operation(summary = "Add a review", description = "Adds a review to a game. A user cannot review the same game twice.")
    @ApiResponse(responseCode = "200", description = "Review added successfully")
    @ApiResponse(responseCode = "409", description = "The user already reviewed this game")
    public ResponseEntity<String> addReview(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId, @Valid @RequestBody AddReviewDTO addReviewDTO){
        gameService.addReview(gameId, addReviewDTO);
        return ResponseEntity.ok("Review added successfully");
    }

    @DeleteMapping("/deleteReview/{reviewId}")
    @Operation(summary = "Delete a review", description = "Removes a review. Can only be done by the author or an Admin.")
    @ApiResponse(responseCode = "200", description = "Review deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this review")
    public ResponseEntity<String> deleteReview(@PathVariable @NotNull  @Size(min = 24, max = 24) String reviewId){
        gameService.deleteReview(reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }

    // INTERESTING QUERIES ============================================
    
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
        @RequestParam(defaultValue = "0") @Min(0) @Max(10) double minRating
    ){
        return ResponseEntity.ok(gameService.getDeals(minReviews, minPrice, maxPrice, minRating));
    }

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

    @GetMapping("/getDiscussed")
    @Operation(
        summary = "Get most actively discussed games",
        description = "Returns games with the most review activity in the shortest time window, based on recent reviews."
    )
    @ApiResponse(responseCode = "200", description = "Games retrieved successfully")
    public ResponseEntity<List<GameStatsDTO>> getDiscussed(){
        return ResponseEntity.ok(gameService.getDiscussed());
    }

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

    @GetMapping("/getGenreStats")
    @Operation(
        summary = "Genre analytics",
        description = "Returns average rating and average hours played per player, grouped by genre. Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<GenreStatsDTO>> getGenreStats(){
        return ResponseEntity.ok(gameService.getGenreStats());
    }

    @GetMapping("/getOsPlatformStats")
    @Operation(
        summary = "OS platform analytics",
        description = "Returns average rating grouped by number of supported operating systems (1, 2, or 3). Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<OsPlatformStatsDTO>> getOsPlatformStats(){
        return ResponseEntity.ok(gameService.getOsPlatformStats());
    }

    @GetMapping("/releaseYearStats")
    @Operation(
        summary = "Release year analytics",
        description = "Returns average rating and total game count grouped by release year. Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<ReleaseYearStatsDTO>> getReleaseYearStats() {
        return ResponseEntity.ok(gameService.getReleaseYearStats());
    }

    @GetMapping("/getRecommendations")
    public ResponseEntity<List<GameRecommendationDTO>> getRecommendations(){
        return ResponseEntity.ok(gameService.getRecommendations());
    }

    @GetMapping("/getTrending")
    public ResponseEntity<List<TrendingGameDTO>> getTrendingGames(){
        return ResponseEntity.ok(gameService.getTrendingGames());
    }

    @GetMapping("/getHiddenGems")
    public ResponseEntity<List<HiddenGemDTO>> getHiddenGems(){
        return ResponseEntity.ok(gameService.getHiddenGems());
    }
}
