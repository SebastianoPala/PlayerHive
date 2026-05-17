package com.unipi.PlayerHive.controller;

import com.unipi.PlayerHive.DTO.reviews.ReviewContainerDTO;
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

@RestController
@RequestMapping("/api/user")

@Tag(name = "4. Users & Social", description = "Profile management, friends, personal libraries, and user rankings")
public class UserController {
    private final UserService userService;

    UserController(UserService userService){
        this.userService = userService;
    }

    public String getAuthenticatedUserId(){
        return ((UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).getUser().getId();
    }

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

    @GetMapping("/MyProfile")
    @Operation(summary = "My profile", description = "Returns the full profile data of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Profile retrieved successfully")
    public ResponseEntity<OwnProfileDTO> showOwnProfile(){
        return ResponseEntity.ok(userService.getOwnProfileById());
    }


    @GetMapping("/search/{query}")
    @Operation(summary = "Search user", description = "Paginated search by username.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    public ResponseEntity<UserSearchContainerDTO> searchUser(@PathVariable String query,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size){
        return ResponseEntity.ok(userService.searchUser(query, page, size));
    }

    @GetMapping("/library/{userId}")
    @Operation(summary = "Show user library", description = "Returns the paginated game library of a specific user.")
    @ApiResponse(responseCode = "200", description = "User library retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<LibraryContainerDTO> showUserLibrary(@PathVariable @NotNull  @Size(min = 24, max = 24) String userId,
                                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                 @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getLibraryById(userId, page, size));
    }

    @GetMapping("/MyLibrary")
    @Operation(summary = "My library", description = "Returns the paginated game library of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Library retrieved successfully")
    public ResponseEntity<LibraryContainerDTO> showOwnLibrary(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "25") @Min(10) @Max(50) int size){
        String requestingUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(userService.getLibraryById(requestingUserId ,page,size));
    }

    @PostMapping("/editLibrary")
    @Operation(summary = "Edit library", description = "Adds a game to the user's library or updates playtime/achievements if already present.")
    @ApiResponse(responseCode = "200", description = "The library has been updated successfully")
    @ApiResponse(responseCode = "403", description = "Invalid arguments (e.g., achievements exceed game limit)")
    @ApiResponse(responseCode = "404", description = "Game not found")
    public ResponseEntity<String> editLibrary(@Valid @RequestBody AddGameToLibraryDTO addGame){
        userService.editLibrary(addGame);
        return ResponseEntity.ok("The library has been updated successfully");
    }
    @DeleteMapping("/removeFromLibrary/{gameId}")
    @Operation(summary = "Remove from library", description = "Removes a game from the user's library and updates stats accordingly.")
    @ApiResponse(responseCode = "200", description = "The game has been removed from the library")
    @ApiResponse(responseCode = "404", description = "Game not found in user's library")
    public ResponseEntity<String> removeFromLibrary(@PathVariable @NotNull @Size(min = 24, max = 24) String gameId){
        userService.removeGameFromLibrary(gameId);
        return ResponseEntity.ok("The library has been updated successfully");
    }

    @GetMapping("/friends/{userId}")
    @Operation(summary = "Show friend list", description = "Returns the paginated friend list of a specific user.")
    @ApiResponse(responseCode = "200", description = "Friend list retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<FriendContainerDTO> showFriendList(@PathVariable @NotNull @Size(min = 24, max = 24) String userId,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getFriendListById(userId, page, size));
    }

    @GetMapping("/MyFriends")
    @Operation(summary = "My friends", description = "Returns the paginated friend list of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Friend list retrieved successfully")
    public ResponseEntity<FriendContainerDTO> showOwnFriendList(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "25") @Min(1) @Max(50) int size){
        String requestingUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(userService.getFriendListById(requestingUserId,page, size));
    }

    @GetMapping("/friendRequests")
    @Operation(summary = "Show friend requests", description = "Returns pending friend requests for the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Friend requests retrieved successfully")
    public ResponseEntity<FriendRequestContainerDTO> showFriendRequests(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getFriendRequests(page,size));
    }

    @PostMapping("/sendFriendRequest/{targetUserId}")
    @Operation(summary = "Send friend request", description = "Sends a friend request to the target user.")
    @ApiResponse(responseCode = "200", description = "Friend request sent successfully / Friendship established")
    @ApiResponse(responseCode = "409", description = "Friend request already present or users are already friends")
    public ResponseEntity<String> sendFriendRequest(@PathVariable @NotNull @Size(min = 24, max = 24) String targetUserId){
        return ResponseEntity.ok(userService.sendRequestToUser(targetUserId));
    }

    @PostMapping("/approveFriendRequest/{targetUserId}")
    @Operation(summary = "Approve friend request", description = "Accepts a pending friend request from the target user.")
    @ApiResponse(responseCode = "200", description = "The friend request has been approved successfully")
    @ApiResponse(responseCode = "404", description = "Friend request not found or user no longer exists")
    public ResponseEntity<String> approveFriendRequest(@PathVariable @NotNull  @Size(min = 24, max = 24) String targetUserId){
        String message = userService.approveRequestFromUser(targetUserId);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/denyFriendRequest/{targetUserId}")
    @Operation(summary = "Deny friend request", description = "Rejects and removes a pending friend request.")
    @ApiResponse(responseCode = "200", description = "Friend request has been denied successfully")
    @ApiResponse(responseCode = "404", description = "Friend request not found")
    public ResponseEntity<String> denyFriendRequest(@PathVariable @NotNull @Size(min = 24, max = 24) String targetUserId){
        userService.removeRequestFromUser(targetUserId);
        return ResponseEntity.ok("Friend request has been denied successfully");
    }

    @DeleteMapping("/removeFriend/{friendId}")
    @Operation(summary = "Remove friend", description = "Removes a user from the authenticated user's friend list.")
    @ApiResponse(responseCode = "200", description = "Friend removed successfully")
    @ApiResponse(responseCode = "404", description = "Friend not found")
    public ResponseEntity<String> removeFriend(@PathVariable @NotNull @Size(min = 24, max = 24) String friendId){
        userService.removeFriend(friendId);
        return ResponseEntity.ok("Friend removed successfully");
    }

    @GetMapping("/reviews/{userId}")
    @Operation(summary = "Get user reviews", description = "Returns the paginated list of reviews written by a specific user.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<ReviewContainerDTO> getUserReviews(@PathVariable @NotNull @Size(min = 24, max = 24) String userId,
                                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                                             @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){
        return ResponseEntity.ok(userService.getUserReviews(userId,page,size));
    }

    @GetMapping("/MyReviews")
    @Operation(summary = "My reviews", description = "Returns the paginated list of reviews written by the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Reviews retrieved successfully")
    public ResponseEntity<ReviewContainerDTO> getOwnReviews(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size){

        return ResponseEntity.ok(userService.getUserReviews(getAuthenticatedUserId(),page,size));
    }

    @DeleteMapping("/deleteAccount")
    @Operation(summary = "Delete account", description = "Permanently deletes the authenticated user's account and all associated data.")
    @ApiResponse(responseCode = "200", description = "Account deleted successfully")
    public ResponseEntity<String> deleteAccount(){

        String userId = getAuthenticatedUserId();
        userService.deleteUser(userId);
        return ResponseEntity.ok("Account Deleted successfully");
    }

    // INTERESTING QUERIES ======================================================
    //TODO ADD VARIABLES
    @GetMapping("/getHardcoreGamers")
    public ResponseEntity<List<PlayerStatsDTO>> getHardcoreGamers(){
        return ResponseEntity.ok(userService.getHardcoreGamers());
    }

    @GetMapping("/getKeyboardWarriors")
    public ResponseEntity<List<KeyboardWarriorDTO>> getKeyboardWarriors(){
        return ResponseEntity.ok(userService.getKeyboardWarriors());
    }

    @GetMapping("/getMostActiveGamers")
    public ResponseEntity<List<ActiveGamerDTO>> getMostActiveGamers(){
        return ResponseEntity.ok(userService.getMostActiveGamers());
    }

    @GetMapping("/friendRecommendations")
    public ResponseEntity<List<FriendRecommendationDTO>> getFriendRecommendations(){
        return ResponseEntity.ok(userService.getFriendRecommendations());
    }

    @GetMapping("/gamingTwins")
    public ResponseEntity<List<GamingTwinDTO>> getGamingTwins(){
        return ResponseEntity.ok(userService.getGamingTwins());
    }

    @GetMapping("/socialButterflies")
    @Operation(summary = "Social Butterflies", description = "Users with the most friends and games played combined.")
    @ApiResponse(responseCode = "200", description = "List returned")
    public ResponseEntity<List<SocialButterflyDTO>> getSocialButterflies(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(userService.getSocialButterflies(limit));
    }

}
