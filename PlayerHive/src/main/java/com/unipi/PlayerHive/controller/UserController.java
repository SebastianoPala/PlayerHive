package com.unipi.PlayerHive.controller;

import com.unipi.PlayerHive.DTO.containers.LibraryContainerDTO;
import com.unipi.PlayerHive.DTO.containers.UserReviewContainerDTO;
import com.unipi.PlayerHive.DTO.containers.UserSearchContainerDTO;
import com.unipi.PlayerHive.DTO.users.*;
import com.unipi.PlayerHive.DTO.users.friends.FriendContainerDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRecommendationDTO;
import com.unipi.PlayerHive.DTO.users.friends.FriendRequestContainerDTO;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller responsible for managing user-related endpoints.
 * Handles profile viewing, game libraries, friend systems, and advanced player analytics.
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "Users & Social", description = "Profile management, friends, personal libraries, and user rankings")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    /**
     * Helper method to retrieve the ID of the currently authenticated user from the Security Context.
     *
     * @return The ID of the authenticated user.
     */
    public String getAuthenticatedUserId(){
        return ((UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).getUser().getId();
    }

    /**
     * Retrieves the public profile of a specific user.
     *
     * @param userId The ID of the user to look up.
     * @return ResponseEntity containing the user profile data.
     */
    @GetMapping("/{userId}")
    @Operation(summary = "View user profile", description = "Returns public data of a user given their ID.")
    @ApiResponse(responseCode = "200", description = "User profile retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<?> showUserProfile(@PathVariable @NotNull  @Size(min = 24, max = 24) String userId){

        // the principal can be either a String or UserPrincipal depending on the user's authentication
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if(principal instanceof UserPrincipal){ // the user is logged in
            String currentUserId = ((UserPrincipal) principal).getUser().getId();
            if(userId.equals(currentUserId)) // the user requested his own profile
                return ResponseEntity.ok(userService.getOwnProfileById());
        }
        return ResponseEntity.ok(userService.getProfileById(userId));
    }

    /**
     * Retrieves the full profile of the currently authenticated user.
     *
     * @return ResponseEntity containing the detailed profile data.
     */
    @GetMapping("/MyProfile")
    @Operation(summary = "My profile", description = "Returns the full profile data of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    public ResponseEntity<OwnProfileDTO> showOwnProfile(){
        return ResponseEntity.ok(userService.getOwnProfileById());
    }

    /**
     * Searches for users matching a specific username query.
     *
     * @param query The partial or full username to search for.
     * @param page  The page number for pagination.
     * @param size  The number of results per page.
     * @return ResponseEntity containing the paginated search results.
     */
    @GetMapping("/search/{query}")
    @Operation(summary = "Search user", description = "Paginated search by username.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<UserSearchContainerDTO> searchUser(@PathVariable String query,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size){
        return ResponseEntity.ok(userService.searchUser(query, page, size));
    }

    /**
     * Retrieves the game library of a specific user.
     *
     * @param userId The ID of the user.
     * @param page   The page number for pagination.
     * @param size   The number of results per page.
     * @return ResponseEntity containing the user's game library.
     */
    @GetMapping("/library/{userId}")
    @Operation(summary = "Show user library", description = "Returns the paginated game library of a specific user.")
    @ApiResponse(responseCode = "200", description = "User library retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<LibraryContainerDTO> showUserLibrary(@PathVariable @NotNull  @Size(min = 24, max = 24) String userId,
                                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                                               @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){

        return ResponseEntity.ok(userService.getLibraryById(userId, page, size));
    }

    /**
     * Retrieves the game library of the currently authenticated user.
     *
     * @param page The page number for pagination.
     * @param size The number of results per page.
     * @return ResponseEntity containing the authenticated user's game library.
     */
    @GetMapping("/MyLibrary")
    @Operation(summary = "My library", description = "Returns the paginated game library of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Library retrieved successfully")
    public ResponseEntity<LibraryContainerDTO> showOwnLibrary(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                              @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        String requestingUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(userService.getLibraryById(requestingUserId ,page,size));
    }

    /**
     * Adds a game to the authenticated user's library or edits its stats if it already exists.
     *
     * @param addGame The details of the game to add or update.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/editLibrary")
    @Operation(summary = "Edit library", description = "Adds a game to the user's library or updates playtime/achievements if already present.")
    @ApiResponse(responseCode = "200", description = "The library has been updated successfully")
    @ApiResponse(responseCode = "403", description = "Invalid arguments (e.g., achievements exceed game limit)")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<String> editLibrary(@Valid @RequestBody AddGameToLibraryDTO addGame){
        userService.editLibrary(addGame);
        return ResponseEntity.ok("The library has been updated successfully");
    }

    /**
     * Removes a game from the authenticated user's library.
     *
     * @param gameId The ID of the game to remove.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/removeFromLibrary/{gameId}")
    @Operation(summary = "Remove from library", description = "Removes a game from the user's library and updates stats accordingly.")
    @ApiResponse(responseCode = "200", description = "The game has been removed from the library")
    @ApiResponse(responseCode = "404", description = "Game not found in user's library")
    public ResponseEntity<String> removeFromLibrary(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId){
        userService.removeGameFromLibrary(gameId);
        return ResponseEntity.ok("The library has been updated successfully");
    }

    /**
     * Retrieves the friend list of a specific user.
     *
     * @param userId The ID of the user.
     * @param page   The page number for pagination.
     * @param size   The number of results per page.
     * @return ResponseEntity containing the friend list.
     */
    @GetMapping("/friends/{userId}")
    @Operation(summary = "Show friend list", description = "Returns the paginated friend list of a specific user.")
    @ApiResponse(responseCode = "200", description = "Friend list retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<FriendContainerDTO> showFriendList(@PathVariable @NotNull @Size(min = 24, max = 24) String userId,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getFriendListById(userId, page, size));
    }

    /**
     * Retrieves the friend list of the currently authenticated user.
     *
     * @param page The page number for pagination.
     * @param size The number of results per page.
     * @return ResponseEntity containing the authenticated user's friend list.
     */
    @GetMapping("/MyFriends")
    @Operation(summary = "My friends", description = "Returns the paginated friend list of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Friend list retrieved successfully")
    public ResponseEntity<FriendContainerDTO> showOwnFriendList(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        String requestingUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(userService.getFriendListById(requestingUserId,page, size));
    }

    /**
     * Retrieves pending friend requests for the currently authenticated user.
     *
     * @param page The page number for pagination.
     * @param size The number of results per page.
     * @return ResponseEntity containing the pending friend requests.
     */
    @GetMapping("/friendRequests")
    @Operation(summary = "Show friend requests", description = "Returns pending friend requests for the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Friend requests retrieved successfully")
    public ResponseEntity<FriendRequestContainerDTO> showFriendRequests(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getFriendRequests(page,size));
    }

    /**
     * Sends a friend request to a target user.
     *
     * @param targetUserId The ID of the target user.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/sendFriendRequest/{targetUserId}")
    @Operation(summary = "Send friend request", description = "Sends a friend request to the target user.")
    @ApiResponse(responseCode = "200", description = "Friend request sent successfully / Friendship established")
    @ApiResponse(responseCode = "409", description = "Friend request already present or users are already friends")
    public ResponseEntity<String> sendFriendRequest(@PathVariable @NotNull @Size(min = 24, max = 24) String targetUserId){
        return ResponseEntity.ok(userService.sendRequestToUser(targetUserId));
    }

    /**
     * Approves a pending friend request from a target user.
     *
     * @param targetUserId The ID of the user whose request is being approved.
     * @return ResponseEntity with a success message.
     */
    @PostMapping("/approveFriendRequest/{targetUserId}")
    @Operation(summary = "Approve friend request", description = "Accepts a pending friend request from the target user.")
    @ApiResponse(responseCode = "200", description = "The friend request has been approved successfully")
    @ApiResponse(responseCode = "404", description = "Friend request not found or user no longer exists")
    public ResponseEntity<String> approveFriendRequest(@PathVariable @NotNull  @Size(min = 24, max = 24) String targetUserId){
        String message = userService.approveRequestFromUser(targetUserId);
        return ResponseEntity.ok(message);
    }

    /**
     * Denies and removes a pending friend request from a target user.
     *
     * @param targetUserId The ID of the user whose request is being denied.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/denyFriendRequest/{targetUserId}")
    @Operation(summary = "Deny friend request", description = "Rejects and removes a pending friend request.")
    @ApiResponse(responseCode = "200", description = "Friend request has been denied successfully")
    @ApiResponse(responseCode = "404", description = "Friend request not found")
    public ResponseEntity<String> denyFriendRequest(@PathVariable @NotNull @Size(min = 24, max = 24) String targetUserId){
        userService.removeRequestFromUser(targetUserId);
        return ResponseEntity.ok("Friend request has been denied successfully");
    }

    /**
     * Removes an established friend from the authenticated user's friend list.
     *
     * @param friendId The ID of the friend to remove.
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/removeFriend/{friendId}")
    @Operation(summary = "Remove friend", description = "Removes a user from the authenticated user's friend list.")
    @ApiResponse(responseCode = "200", description = "Friend removed successfully")
    @ApiResponse(responseCode = "404", description = "Friend not found")
    public ResponseEntity<String> removeFriend(@PathVariable @NotNull @Size(min = 24, max = 24) String friendId){
        userService.removeFriend(friendId);
        return ResponseEntity.ok("Friend removed successfully");
    }

    /**
     * Retrieves a paginated list of reviews written by a specific user.
     *
     * @param userId The ID of the user.
     * @param page   The page number for pagination.
     * @param size   The number of results per page.
     * @return ResponseEntity containing the user's reviews.
     */
    @GetMapping("/reviews/{userId}")
    @Operation(summary = "Get user reviews", description = "Returns the paginated list of reviews written by a specific user.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserReviewContainerDTO> getUserReviews(@PathVariable @NotNull @Size(min = 24, max = 24) String userId,
                                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                 @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getUserReviews(userId,page,size));
    }

    /**
     * Retrieves a paginated list of reviews written by the currently authenticated user.
     *
     * @param page The page number for pagination.
     * @param size The number of results per page.
     * @return ResponseEntity containing the authenticated user's reviews.
     */
    @GetMapping("/MyReviews")
    @Operation(summary = "My reviews", description = "Returns the paginated list of reviews written by the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    public ResponseEntity<UserReviewContainerDTO> getOwnReviews(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){

        return ResponseEntity.ok(userService.getUserReviews(getAuthenticatedUserId(),page,size));
    }

    /**
     * Permanently deletes the currently authenticated user's account and all their related data.
     *
     * @return ResponseEntity with a success message.
     */
    @DeleteMapping("/deleteAccount")
    @Operation(summary = "Delete account", description = "Permanently deletes the authenticated user's account and all associated data.")
    @ApiResponse(responseCode = "200", description = "Account deleted successfully")
    public ResponseEntity<String> deleteAccount(){

        String userId = getAuthenticatedUserId();
        userService.deleteUser(userId);
        return ResponseEntity.ok("Account Deleted successfully");
    }

    // INTERESTING QUERIES ======================================================

    /**
     * Retrieves a list of hardcore gamers who exceed given minimums for games owned and hours played.
     *
     * @param minGames Minimum number of games owned.
     * @param minHours Minimum total hours played.
     * @return ResponseEntity containing the list of hardcore gamers.
     */
    @GetMapping("/getHardcoreGamers")
    @Operation(summary = "Get hardcore gamers", description = "Retrieves a list of users with a high number of games and playtime.")
    @ApiResponse(responseCode = "200", description = "List of hardcore gamers retrieved successfully")
    public ResponseEntity<List<PlayerStatsDTO>> getHardcoreGamers(@RequestParam(defaultValue = "5") @Min(1) int minGames,
                                                                  @RequestParam(defaultValue = "100") @Min(100) double minHours){
        return ResponseEntity.ok(userService.getHardcoreGamers(minGames,minHours));
    }

    /**
     * Retrieves a list of users identified as "Keyboard Warriors" based on their reviews-to-games ratio.
     *
     * @param warriorRatio The minimum review-to-game ratio to qualify.
     * @return ResponseEntity containing the list of keyboard warriors.
     */
    @GetMapping("/getKeyboardWarriors")
    @Operation(summary = "Get keyboard warriors", description = "Retrieves users who review exceptionally high amounts of games relative to their library size.")
    @ApiResponse(responseCode = "200", description = "List of keyboard warriors retrieved successfully")
    public ResponseEntity<List<KeyboardWarriorDTO>> getKeyboardWarriors(@RequestParam(defaultValue = "1") @Min(1) double warriorRatio){
        return ResponseEntity.ok(userService.getKeyboardWarriors(warriorRatio));
    }

    /**
     * Retrieves a list of the most active gamers based on the frequency of adding games to their library.
     *
     * @return ResponseEntity containing the list of most active gamers.
     */
    @GetMapping("/getMostActiveGamers")
    @Operation(summary = "Get most active gamers", description = "Retrieves gamers with the highest daily activity rate based on registration date.")
    @ApiResponse(responseCode = "200", description = "List of active gamers retrieved successfully")
    public ResponseEntity<List<ActiveGamerDTO>> getMostActiveGamers(){
        return ResponseEntity.ok(userService.getMostActiveGamers());
    }

    /**
     * Generates a list of friend recommendations for the authenticated user using Triadic Closure.
     *
     * @return ResponseEntity containing recommended friends.
     */
    @GetMapping("/friendRecommendations")
    @Operation(summary = "Get friend recommendations", description = "Suggests new friends based on mutual connections.")
    @ApiResponse(responseCode = "200", description = "Friend recommendations retrieved successfully")
    public ResponseEntity<List<FriendRecommendationDTO>> getFriendRecommendations(){
        return ResponseEntity.ok(userService.getFriendRecommendations());
    }

    /**
     * Finds users with a highly similar game library to the authenticated user ("Gaming Twins").
     *
     * @return ResponseEntity containing a list of similar users.
     */
    @GetMapping("/gamingTwins")
    @Operation(summary = "Get gaming twins", description = "Finds users with game libraries very similar to yours using Jaccard Similarity.")
    @ApiResponse(responseCode = "200", description = "Gaming twins retrieved successfully")
    public ResponseEntity<List<GamingTwinDTO>> getGamingTwins(){
        return ResponseEntity.ok(userService.getGamingTwins());
    }

}