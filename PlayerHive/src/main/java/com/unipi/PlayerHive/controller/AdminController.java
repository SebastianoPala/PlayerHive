package com.unipi.PlayerHive.controller;


import com.unipi.PlayerHive.DTO.analytics.GenreStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.OsPlatformStatsDTO;
import com.unipi.PlayerHive.DTO.analytics.ReleaseYearStatsDTO;
import com.unipi.PlayerHive.DTO.games.AddGameDTO;
import com.unipi.PlayerHive.DTO.games.EditGameDTO;
import com.unipi.PlayerHive.DTO.games.TrendingGameDTO;
import com.unipi.PlayerHive.service.AdminService;
import com.unipi.PlayerHive.service.GameService;
import com.unipi.PlayerHive.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Administrator operations.
 * Allows adding, modifying, and deleting games, force-deleting users, and viewing platform-wide analytics.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin operations (Game and User management)")
public class AdminController {
    private final AdminService adminService;
    private final UserService userService;
    private final GameService gameService;

    public AdminController(AdminService adminService, UserService userService, GameService gameService) {
        this.adminService = adminService;
        this.userService = userService;
        this.gameService = gameService;
    }

    /**
     * Adds a new game to the platform databases.
     *
     * @param newGame The DTO containing the details of the game to add.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/addGame")
    @Operation(summary = "Add a new game", description = "Inserts a new game into the database (MongoDB and Neo4j).")
    @ApiResponse(responseCode = "200", description = "The game has been added successfully")
    @ApiResponse(responseCode = "409", description = "The game already exists")
    public ResponseEntity<String> addGame(@Valid @RequestBody AddGameDTO newGame){
        adminService.addGame(newGame);
        return ResponseEntity.ok("The game has been added successfully");
    }

    /**
     * Updates an existing game's metadata.
     *
     * @param gameId   The ID of the game to edit.
     * @param editGame The DTO containing the updated game fields.
     * @return ResponseEntity with a success message.
     */
    @PatchMapping("/editGame/{gameId}")
    @Operation(summary = "Edit a game", description = "Updates the details of an existing game by its ID.")
    @ApiResponse(responseCode = "200", description = "The game info has been edited successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<String> editGame(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId, @RequestBody EditGameDTO editGame){
        adminService.editGame(gameId,editGame);
        return ResponseEntity.ok("The game info has been edited successfully");
    }

    /**
     * Deletes a game and cleans up all related statistics and reviews.
     *
     * @param gameId The ID of the game to delete.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/deleteGame/{gameId}")
    @Operation(summary = "Delete a game", description = "Permanently removes a game, its reviews, and updates user statistics.")
    @ApiResponse(responseCode = "200", description = "The game has been deleted successfully")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<String> deleteGame(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId){
        adminService.deleteGame(gameId);
        return ResponseEntity.ok("The game has been deleted successfully");
    }

    /**
     * Forcefully deletes a user account and all associated data.
     *
     * @param userId The ID of the user to delete.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/deleteUser/{userId}")
    @Operation(summary = "Force delete a user", description = "Allows an admin to remove a user and all related data.")
    @ApiResponse(responseCode = "200", description = "The user has been deleted successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<String> deleteUser(@PathVariable @NotNull @Size(min = 24, max = 24) String userId){
        userService.deleteUser(userId);
        return  ResponseEntity.ok("The user has been deleted successfully");
    }

    // analytics

    /**
     * Retrieves games that are currently trending across social clusters.
     *
     * @param limit     The maximum number of games to return.
     * @param minSocial Minimum social interactions required to be trending.
     * @return ResponseEntity containing a list of trending games.
     */
    @GetMapping("/getTrending")
    @Operation(summary = "Get trending games", description = "Retrieves a list of games currently trending among social friend groups.")
    @ApiResponse(responseCode = "200", description = "Trending games retrieved successfully")
    public ResponseEntity<List<TrendingGameDTO>> getTrendingGames(@RequestParam(defaultValue = "20") @Min(10) int limit,
                                                                  @RequestParam(defaultValue = "1") @Min(1) int minSocial){
        return ResponseEntity.ok(gameService.getTrendingGames(limit, minSocial));
    }

    /**
     * Retrieves analytical statistics detailing platform-wide averages grouped by game genre.
     *
     * @return ResponseEntity containing genre statistics.
     */
    @GetMapping("/getGenreStats")
    @Operation(
            summary = "Genre analytics",
            description = "Returns average rating and average hours played per player, grouped by genre. Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<GenreStatsDTO>> getGenreStats(){
        return ResponseEntity.ok(gameService.getGenreStats());
    }

    /**
     * Retrieves analytical statistics grouped by OS compatibility quantity.
     *
     * @return ResponseEntity containing OS platform statistics.
     */
    @GetMapping("/getOsPlatformStats")
    @Operation(
            summary = "OS platform analytics",
            description = "Returns average rating grouped by number of supported operating systems (1, 2, or 3). Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<OsPlatformStatsDTO>> getOsPlatformStats(){
        return ResponseEntity.ok(gameService.getOsPlatformStats());
    }

    /**
     * Retrieves analytical statistics indicating platform-wide averages grouped by release year.
     *
     * @return ResponseEntity containing release year statistics.
     */
    @GetMapping("/releaseYearStats")
    @Operation(
            summary = "Release year analytics",
            description = "Returns average rating and total game count grouped by release year. Admin only."
    )
    @ApiResponse(responseCode = "200", description = "Stats retrieved successfully")
    public ResponseEntity<List<ReleaseYearStatsDTO>> getReleaseYearStats() {
        return ResponseEntity.ok(gameService.getReleaseYearStats());
    }
}