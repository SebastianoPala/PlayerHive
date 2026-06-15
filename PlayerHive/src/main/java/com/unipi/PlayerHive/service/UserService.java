package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.containers.LibraryContainerDTO;
import com.unipi.PlayerHive.DTO.containers.OldUserReviewContainerDTO;
import com.unipi.PlayerHive.DTO.containers.UserReviewContainerDTO;
import com.unipi.PlayerHive.DTO.containers.UserSearchContainerDTO;
import com.unipi.PlayerHive.DTO.games.LibraryGameDTO;
import com.unipi.PlayerHive.DTO.games.PlaytimeAchievementsDTO;
import com.unipi.PlayerHive.DTO.reviews.*;
import com.unipi.PlayerHive.DTO.users.*;
import com.unipi.PlayerHive.DTO.users.friends.*;
import com.unipi.PlayerHive.config.Exceptions.ResourceAlreadyExistsException;
import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.repository.ReviewRepository;
import com.unipi.PlayerHive.repository.games.GameNeo4jRepository;
import com.unipi.PlayerHive.repository.games.GameRepository;
import com.unipi.PlayerHive.repository.users.UserNeo4jRepository;
import com.unipi.PlayerHive.repository.users.UserRepository;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.utility.batch.GameConsistencyManager;
import com.unipi.PlayerHive.utility.map.UserMapper;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service class handling user-related operations, including profile management,
 * library tracking, social features (friends), and complex analytical queries.
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserNeo4jRepository userNeo4jRepository;
    private final GameRepository gameRepository;
    private final GameNeo4jRepository gameNeo4jRepository;
    private final UserMapper userMapper;

    private final GameConsistencyManager gameConsistencyManager;

    private final ReviewRepository reviewRepository;

    public UserService(UserRepository userRepository, UserNeo4jRepository userNeo4jRepository, GameRepository gameRepository, UserMapper userMapper, GameNeo4jRepository gameNeo4jRepository, GameConsistencyManager gameConsistencyManager, ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.userNeo4jRepository = userNeo4jRepository;
        this.gameRepository = gameRepository;
        this.userMapper = userMapper;
        this.gameNeo4jRepository = gameNeo4jRepository;
        this.gameConsistencyManager = gameConsistencyManager;
        this.reviewRepository = reviewRepository;
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
     * Retrieves the public profile information of a specific user.
     *
     * @param userId The ID of the requested user.
     * @return A ProfileDTO containing the public user data.
     * @throws NoSuchElementException if the user does not exist.
     */
    public ProfileDTO getProfileById(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found"));
        return userMapper.userToProfileDTO(user);
    }

    /**
     * Retrieves the complete profile information for the currently authenticated user.
     *
     * @return An OwnProfileDTO containing detailed user data including pending friend requests.
     */
    public OwnProfileDTO getOwnProfileById() {

        // more information is provided if the user requests his own profile
        OwnProfileMongoDTO ownProfileMongo = userRepository.getOwnProfile(getAuthenticatedUser().getId());

        OwnProfileDTO ownProfileDTO = userMapper.OwnProfileMongoToOwnProfileDTO(ownProfileMongo);

        // embedded userIds are converted from ObjectId to String
        ownProfileDTO.setFriendRequests(ownProfileMongo.getFriendRequestsMongo()
                .stream().map(mongo ->
                        new FriendRequestDTO(mongo.getUserId().toString(),
                                mongo.getUsername(),
                                mongo.getPfpURL(),
                                mongo.getTimestamp())
                ).toList());

        return ownProfileDTO;
    }

    /**
     * Searches for users by username using a case-insensitive partial match.
     *
     * @param username The partial username to search for.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container holding the paginated search results.
     */
    public UserSearchContainerDTO searchUser(String username, int page, int size) {

        Pageable pageable = PageRequest.of(page,size);

        Slice<UserSearchDTO> result = userRepository.searchByUsernameContaining(username, pageable);

        return new UserSearchContainerDTO(result.getContent(),result.isLast());
    }

    /**
     * Retrieves the game library for a specific user.
     *
     * @param userId The ID of the user.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container holding the paginated library items.
     * @throws NoSuchElementException if the user does not exist.
     */
    public LibraryContainerDTO getLibraryById(String userId, int page, int size) {

        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The requested user does not exist");

        Pageable pageable = PageRequest.of(page,size);
        Page<LibraryGameDTO> library = userNeo4jRepository.findLibraryById(userId, pageable);

        return new LibraryContainerDTO(library.getContent(), library.getTotalPages(), library.isLast());
    }

    /**
     * Adds a game to the authenticated user's library or updates existing playtime/achievements.
     * Modifies aggregate statistics on both the user and the game documents.
     *
     * @param addGame The DTO containing the game ID, playtime, and achievements.
     * @throws IllegalArgumentException if the ID format is invalid or achievements exceed the game's max.
     * @throws NoSuchElementException if the game does not exist.
     * @throws RuntimeException if database updates fail.
     */
    @Transactional
    public void editLibrary(@Valid AddGameToLibraryDTO addGame) {

        if(addGame.getGameId().length() != 24)
            throw new IllegalArgumentException("The provided game Id is not valid");

        if(!gameRepository.existsById(addGame.getGameId()))
            throw new NoSuchElementException("The requested game does not exist");

        String userId = getAuthenticatedUser().getId();

        // PlaytimeAchievementsDTO contains (if present) the user's playtime on a game, and said game's total number of achievements.
        // the game's achievement number is retrieved (and not the user's) in order to avoid performing two separate queries
        PlaytimeAchievementsDTO playAchiev = gameNeo4jRepository.findUserPlaytimeAndGameAchievements(userId, addGame.getGameId());

        boolean gameAlreadyPresent = playAchiev.getHoursPlayed() != null;

        if(playAchiev.getAchievements() < addGame.getAchievements())
            throw new IllegalArgumentException("The achievement number exceeds the game's achievement number");

        float playtimeToAdd = addGame.getHoursPlayed();

        if (gameAlreadyPresent) {
            playtimeToAdd -= playAchiev.getHoursPlayed().floatValue(); // if the game was already in the library, we only add the difference
        }

        if( playtimeToAdd == 0 && gameAlreadyPresent)
            return; // nothing to update

        boolean success = userNeo4jRepository.saveGameInLibrary(userId,addGame.getGameId(),addGame.getHoursPlayed().doubleValue(),addGame.getAchievements());
        if(!success)
            throw new RuntimeException("The server was unable to add the game to the library");

        int modified = userRepository.updateUserStats(userId, playtimeToAdd,(gameAlreadyPresent) ? 0 : 1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to increase the player's gaming stats");

        modified = gameRepository.updateGameStats(addGame.getGameId(), playtimeToAdd,(gameAlreadyPresent) ? 0 : 1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to increase the game's stats");

    }

    /**
     * Removes a game from the authenticated user's library and adjusts total statistics.
     *
     * @param gameId The ID of the game to remove.
     * @throws NoSuchElementException if the game is not in the user's library.
     * @throws RuntimeException if database updates fail.
     */
    @Transactional
    public void removeGameFromLibrary(String gameId) {
        String userId = getAuthenticatedUser().getId();

        Double userGamePlaytime = userNeo4jRepository.removeGameAndGetPlaytime(userId, gameId)
                .orElseThrow(() -> new NoSuchElementException("The game specified is not present in the user's library"));

        int modified = userRepository.updateUserStats(userId, -userGamePlaytime.floatValue(),-1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to decrease the player's gaming stats");

        modified = gameRepository.updateGameStats(gameId, -userGamePlaytime.floatValue(),-1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to decrease the game's stats");
    }

    /**
     * Retrieves the paginated friend list for a specific user.
     *
     * @param userId The ID of the user.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container holding the paginated friend list.
     * @throws NoSuchElementException if the user does not exist.
     */
    public FriendContainerDTO getFriendListById(String userId, int page, int size) {
        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The requested user does not exist");
        Pageable pageable = PageRequest.of(page,size);
        Page<FriendDTO> friends = userNeo4jRepository.findUsersFriends(userId, pageable);

        return new FriendContainerDTO(friends.getContent(), friends.getTotalPages(), friends.isLast());
    }

    /**
     * Retrieves the paginated incoming friend requests for the authenticated user.
     *
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container holding the paginated friend requests.
     */
    public FriendRequestContainerDTO getFriendRequests(int page, int size) {
        String userId = getAuthenticatedUser().getId();
        int friendRequestNumber = getAuthenticatedUser().getRequestsNum();

        int skip = page * size;

        List<FriendRequestMongoDTO> friendRequestMongoDTO = userRepository.findFriendRequestsById(userId,skip,size).getFriendRequests();

        //embedded friend requests save the user_id field as an Object id, but we want to return it as a string

        List<FriendRequestDTO> friendRequests = friendRequestMongoDTO.stream()
                .map(mongo ->
                        new FriendRequestDTO(mongo.getUserId().toString(),
                                mongo.getUsername(),
                                mongo.getPfpURL(),
                                mongo.getTimestamp())
                ).toList();

        int numPages = (friendRequestNumber / size) + ((friendRequestNumber % size > 0) ? 1 : 0);
        boolean isLastPage = (friendRequestNumber - skip <= size);

        return new FriendRequestContainerDTO(friendRequests,getAuthenticatedUser().getRequestsNum(),numPages,isLastPage);
    }

    /**
     * Sends a friend request to a target user. If the target user has already sent a request
     * to the authenticated user, it automatically approves the friendship instead.
     *
     * @param targetUserId The ID of the target user.
     * @return A success message indicating the action taken.
     * @throws IllegalArgumentException if the user targets themselves.
     * @throws NoSuchElementException if the target user does not exist.
     * @throws ResourceAlreadyExistsException if they are already friends or the request exists.
     */
    @Transactional
    public String sendRequestToUser(String targetUserId) {

        User user = getAuthenticatedUser();
        String userId = user.getId();

        if(userId.equalsIgnoreCase(targetUserId))
            throw new IllegalArgumentException("The user attempted to send a request to himself");

        if(!userRepository.existsById(targetUserId))
            throw new NoSuchElementException("The specified user does not exist");

        if(userNeo4jRepository.checkFriendshipExistence(userId,targetUserId))
            throw new ResourceAlreadyExistsException("The users are already friends");

        try { // we first check if we already have a request from targetUser
            this.approveRequestFromUser(targetUserId);
            return "The friendship has been established";

        } catch (NoSuchElementException ignored) {} // if no friend request was present, NoSuchElementException is thrown

        FriendRequestMongoDTO requestDTO = new FriendRequestMongoDTO(new ObjectId(userId),user.getUsername(),user.getPfpURL(), LocalDateTime.now());

        int modified = userRepository.addFriendRequest(targetUserId,requestDTO.getUserId(),requestDTO);
        if(modified != 1)
            throw new ResourceAlreadyExistsException("The friend request is already present");

        return "Friend request sent successfully";
    }

    /**
     * Approves a pending friend request from a target user and establishes the friendship connection.
     *
     * @param targetUserId The ID of the user who sent the request.
     * @return A success message.
     * @throws IllegalArgumentException if the ID format is invalid.
     * @throws NoSuchElementException if the request or the target user does not exist.
     * @throws RuntimeException if database updates fail.
     */
    @Transactional
    public String approveRequestFromUser(String targetUserId) {
        String userId = getAuthenticatedUser().getId();

        if(targetUserId.length() != 24) // prevents ObjectId constructor exception
            throw new IllegalArgumentException("The input given is not a valid user Id");

        int result;

        if(!userRepository.existsById(targetUserId)){
            // cleaning up the friend request of a deleted user
            result = userRepository.removeFriendRequest(userId,new ObjectId(targetUserId));
            if(result == 1)
                return "The profile that sent the friend request no longer exists. The friend request has been removed";
            else
                throw new NoSuchElementException("Friend request was not present!");
        }

        result = userRepository.acceptFriendRequest(userId,new ObjectId(targetUserId));
        if(result != 1)
            throw new NoSuchElementException("Friend request was not present!");

        result = userRepository.editFriendCounter(targetUserId, 1);
        if(result != 1)
            throw new RuntimeException("The server couldn't increase the user's friend counter");

        boolean success = userNeo4jRepository.createFriendship(userId,targetUserId);
        if(!success)
            throw new RuntimeException("The server was unable to complete the operation");

        return "The friend request has been approved successfully";
    }

    /**
     * Rejects/removes an incoming friend request without establishing a friendship.
     *
     * @param targetUserId The ID of the user who sent the request.
     * @throws NoSuchElementException if the request does not exist.
     */
    public void removeRequestFromUser(String targetUserId) {
        String userId = getAuthenticatedUser().getId();

        int result = userRepository.removeFriendRequest(userId,new ObjectId(targetUserId));
        if(result != 1)
            throw new NoSuchElementException("Friend request was not present!");
    }

    /**
     * Removes an established friend connection between the authenticated user and a target friend.
     *
     * @param friendId The ID of the friend to remove.
     * @throws IllegalArgumentException if the user attempts to remove themselves.
     * @throws NoSuchElementException if the friendship does not exist.
     * @throws RuntimeException if the counter update fails.
     */
    @Transactional
    public void removeFriend(String friendId) {
        String userId = getAuthenticatedUser().getId();

        if(userId.equals(friendId))
            throw new IllegalArgumentException("The user is attempting to remove himself");

        boolean success = userNeo4jRepository.removeFriendById(userId,friendId);
        if(!success)
            throw new NoSuchElementException("No Friend was found matching the given Id");

        int result = userRepository.decrementFriendCounterForUsers(List.of(userId, friendId));
        if (result != 2)
            throw new RuntimeException("The server was unable to decrease the friend counters");
    }

    /**
     * Retrieves the paginated list of reviews authored by a specific user.
     *
     * @param userId The ID of the user.
     * @param page The pagination page number.
     * @param size The number of results per page.
     * @return A container holding the user's reviews.
     * @throws NoSuchElementException if the user does not exist.
     */
    public UserReviewContainerDTO getUserReviews(String userId, int page, int size) {
        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The user does not exist");

        int skip = page * size;

        OldUserReviewContainerDTO reviewContainer = userRepository.getUserReviews(userId, skip, size);

        List<OldUserReviewDTO> userReviews = reviewContainer.getReviews();

        int reviewNumber =  reviewContainer.getReviewsNum();

        List<String> reviewIds = userReviews.stream().map(userReviewDTO -> userReviewDTO.getReviewId().toString()).toList();

        List<UserReviewDTO> reviews = reviewRepository.findUserReviewsByIdIn(reviewIds);

        int numPages = (reviewNumber / size) + ((reviewNumber % size > 0) ? 1 : 0);
        boolean isLastPage = (reviewNumber - skip <= size);

        return new UserReviewContainerDTO(reviews,numPages,isLastPage);
    }

    /**
     * Deletes a user profile and propagates the deletion to clean up social connections,
     * authored reviews, and associated game statistics.
     * Requires the requester to be the target user or an ADMIN.
     *
     * @param userId The ID of the user to delete.
     * @throws IllegalArgumentException if a standard user tries to delete another account.
     * @throws NoSuchElementException if the user does not exist.
     */
    @Transactional
    public void deleteUser(String userId){

        User requestingUser = getAuthenticatedUser();
        String requesterId = requestingUser.getId();

        boolean isAdmin = requestingUser.getRole().equalsIgnoreCase("ADMIN");

        if(!isAdmin) {
            if (!requesterId.equals(userId))
                throw new IllegalArgumentException("You can't delete another user's profile");

        } else if (!userRepository.existsById(userId))
            throw new NoSuchElementException("The user requested for deletion does not exist");

        System.out.println("A user with Id: " + userId + " has been scheduled for deletion");

        List<String> friendList = userNeo4jRepository.findAllUsersFriend(userId);

        System.out.println("The target user has " + friendList.size() + " friends");

        if(!friendList.isEmpty()) {
            // decrements the "friend" value of every user found in the previous query
            long modified = userRepository.decrementFriendCounterForUsers(friendList);

            System.out.println(modified + " users had their friend counter decreased");

            friendList.clear();
        }

        // we do not delete friend requests, they will be deleted eventually, when the receiver interacts with them

        // we remove the user's reviews from every single game
        long modified = gameConsistencyManager.removeUserReviewsFromGames(userId);

        System.out.println(modified + " games had their reviews updated");

        modified = gameConsistencyManager.adjustGameStatsAndRemoveUserNode(userId);

        System.out.println(modified + " games had their stats updated");

        userRepository.deleteById(userId);
    }

    // INTERESTING QUERIES ===========================================

    /**
     * Retrieves a list of hardcore gamers based on minimum played games and playtime thresholds.
     *
     * @param minGames Minimum games in library.
     * @param minHours Minimum total hours played.
     * @return List of matching PlayerStatsDTOs.
     */
    public List<PlayerStatsDTO> getHardcoreGamers(int minGames, double minHours){
        return userRepository.findHardcoreGamers(minGames, minHours);
    }

    /**
     * Retrieves a list of users with high review-to-game ratios ("Keyboard Warriors").
     *
     * @param warriorRatio Minimum threshold ratio.
     * @return List of matching KeyboardWarriorDTOs.
     */
    public List<KeyboardWarriorDTO> getKeyboardWarriors(double warriorRatio){
        return userRepository.getKeyboardWarriors(warriorRatio);
    }

    /**
     * Retrieves the most active gamers based on the number of games played per day since registration.
     *
     * @return List of matching ActiveGamerDTOs.
     */
    public List<ActiveGamerDTO> getMostActiveGamers(){
        return userRepository.getMostActiveGamers();
    }

    /**
     * Generates friend recommendations for the authenticated user based on mutual connections.
     *
     * @return List of recommended users.
     */
    public List<FriendRecommendationDTO> getFriendRecommendations(){
        String userId = getAuthenticatedUser().getId();
        return userNeo4jRepository.getFriendRecommendations(userId,10);
    }

    /**
     * Generates "Gaming Twin" matches for the authenticated user based on shared game libraries.
     *
     * @return List of gaming twins ranked by similarity.
     */
    public List<GamingTwinDTO> getGamingTwins(){
        String userId = getAuthenticatedUser().getId();
        return userNeo4jRepository.getGamingTwins(userId, 10);
    }
}