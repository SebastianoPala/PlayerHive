package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.DTO.games.LibraryGameDTO;
import com.unipi.PlayerHive.DTO.games.PlaytimeAchievementsDTO;
import com.unipi.PlayerHive.DTO.reviews.ReviewContainerDTO;
import com.unipi.PlayerHive.DTO.reviews.ReviewDTO;
import com.unipi.PlayerHive.DTO.reviews.UserReviewDTO;
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
import com.unipi.PlayerHive.utility.ArrayPager;
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

    // JwtFilter already put the authenticated user in the security context earlier in the request, this just reads it back out :)
    private User getAuthenticatedUser() {
        return ((UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal())
                .getUser();
    }

    public ProfileDTO getProfileById(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found"));
        return userMapper.userToProfileDTO(user);
    }

    public OwnProfileDTO getOwnProfileById() {

        User user = getAuthenticatedUser();

        // more information is provided if the user requests his own profile
        OwnProfileDTO ownProfile = userMapper.userToOwnProfileDTO(user);

        ownProfile.setFriendRequestsNumber(userRepository.getFriendRequestsNumber(user.getId()));

        return ownProfile;
    }

    public UserSearchContainerDTO searchUser(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);

        Slice<UserSearchDTO> result = userRepository.searchByUsernameContaining(username, pageable);
        return new UserSearchContainerDTO(result.getContent(),result.isLast());
    }

    public LibraryContainerDTO getLibraryById(String userId, int page, int size) {

        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The requested user does not exist");

        Pageable pageable = PageRequest.of(page,size);
        Page<LibraryGameDTO> library = userNeo4jRepository.findLibraryById(userId, pageable);

        return new LibraryContainerDTO(library.getContent(), library.getTotalPages(), library.isLast());
    }

    @Transactional
    public void editLibrary(@Valid AddGameToLibraryDTO addGame) {
        // TODO: add to the exception controller the validation failure exception (negative achievements)

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

    @Transactional
    public void removeGameFromLibrary(String gameId) {
        String userId = getAuthenticatedUser().getId();

        Double userGamePlaytime = userNeo4jRepository.removeGameAndGetPlaytime(userId, gameId)
                .orElseThrow(() -> new NoSuchElementException("The game specified is not present in the user's library"));

        int modified = userRepository.updateUserStats(userId, -userGamePlaytime.floatValue(),-1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to decrease the player's gaming stats");

        // TODO
        modified = gameRepository.updateGameStats(gameId, -userGamePlaytime.floatValue(),-1);
        if(modified<=0)
            throw new RuntimeException("The server was unable to decrease the game's stats");
    }

    public FriendContainerDTO getFriendListById(String userId, int page, int size) {
        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The requested user does not exist");
        Pageable pageable = PageRequest.of(page,size);
        Page<FriendDTO> friends = userNeo4jRepository.findUsersFriends(userId, pageable);

        return new FriendContainerDTO(friends.getContent(), friends.getTotalPages(), friends.isLast());
    }

    public FriendRequestContainerDTO getFriendRequests(int page, int size) {
        String userId = getAuthenticatedUser().getId();

        int friendRequestNumber = userRepository.getFriendRequestsNumber(userId);

        // new friend requests are appended to the array, ArrayPager is needed
        ArrayPager pager = new ArrayPager(friendRequestNumber, page, size);

        List<FriendRequestDTO> friendRequests = new ArrayList<>();
        int numPages;
        boolean isLastPage;

        if(friendRequestNumber >= 0 && !pager.isOutOfBounds()){

            List<FriendRequestMongoDTO> friendRequestsMongo = userRepository.findFriendRequestsById(userId,pager.getStart(),pager.getLimit()).getFriendRequests();

            for (int i = friendRequestsMongo.size() - 1; i >= 0; i--){
                FriendRequestMongoDTO fm = friendRequestsMongo.get(i);
                friendRequests.add(new FriendRequestDTO(fm.getUserId().toString(),fm.getUsername(),fm.getPfpURL(),fm.getTimestamp()));
            }
            // friend requests are now in chronological order

            numPages = pager.getNumPages();
            isLastPage = pager.isLastPage();
        } else {
            numPages = 0;
            isLastPage = true;
        }

        return new FriendRequestContainerDTO(friendRequests,numPages,isLastPage);
    }

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

        // the friend request is appended to the array
        int modified = userRepository.addFriendRequest(targetUserId,requestDTO.getUserId(),requestDTO);
        if(modified != 1)
            throw new ResourceAlreadyExistsException("The friend request is already present");

        return "Friend request sent successfully";
    }

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

    public void removeRequestFromUser(String targetUserId) {
        String userId = getAuthenticatedUser().getId();

        //if(!userRepository.existsById(userId)) if the user does not exist, the request will simply not be present
        // ...

        int result = userRepository.removeFriendRequest(userId,new ObjectId(targetUserId));
        if(result != 1)
            throw new NoSuchElementException("Friend request was not present!");
    }

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

    public ReviewContainerDTO getUserReviews(String userId, int page, int size) {
        if(!userRepository.existsById(userId))
            throw new NoSuchElementException("The user does not exist");

        List<ReviewDTO> reviews;
        int numPages;
        boolean isLastPage;

        int reviewNumber = userRepository.getReviewNumber(userId);

        // new user reviews are appended to the array, Array Pager is required
        ArrayPager pager = new ArrayPager(reviewNumber, page, size);

        if(reviewNumber >= 0 && !pager.isOutOfBounds()) {

            List<UserReviewDTO> userReviews = userRepository.getUserReviews(userId, pager.getStart(), pager.getLimit()).getReviews();

            List<String> reviewIds = userReviews.stream().map(userReviewDTO -> userReviewDTO.getReviewId().toString()).toList();

            reviews = reviewRepository.findByIdInOrderByTimestampDesc(reviewIds);
            numPages = pager.getNumPages();
            isLastPage = pager.isLastPage();
        } else{
            reviews = new ArrayList<>();
            numPages = 0;
            isLastPage = true;
        }

        return new ReviewContainerDTO(reviews,numPages,isLastPage);
    }


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

        // we do not delete friend requests, they will be deleted eventually

        // we remove the user's reviews from every single game
        long modified = gameConsistencyManager.removeUserReviewsFromGames(userId);

        System.out.println(modified + " games had their reviews updated");

        // deletes all user's reviews
        modified = reviewRepository.removeByUserId(new ObjectId(userId));

        System.out.println(modified + " reviews were deleted from the Review Collection");


        modified = gameConsistencyManager.adjustGameStatsAndRemoveUserNode(userId);

        System.out.println(modified + " games had their stats updated");

        userRepository.deleteById(userId);
    }

    // INTERESTING QUERIES ===========================================

    //TODO ADD VARIABLES
    public List<PlayerStatsDTO> getHardcoreGamers(){
        return userRepository.findHardcoreGamers(5, 100);
    }

    public List<KeyboardWarriorDTO> getKeyboardWarriors(){
        return userRepository.getKeyboardWarriors();
    }

    public List<ActiveGamerDTO> getMostActiveGamers(){
        return userRepository.getMostActiveGamers();
    }

    public List<FriendRecommendationDTO> getFriendRecommendations(){
        String userId = getAuthenticatedUser().getId();
        return userNeo4jRepository.getFriendRecommendations(userId,10);
    }

    public List<GamingTwinDTO> getGamingTwins(){
        String userId = getAuthenticatedUser().getId();
        return userNeo4jRepository.getGamingTwins(userId, 10);
    }

}
