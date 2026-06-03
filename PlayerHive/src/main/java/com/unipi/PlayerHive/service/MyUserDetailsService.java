package com.unipi.PlayerHive.service;

import com.unipi.PlayerHive.model.user.User;
import com.unipi.PlayerHive.model.user.UserPrincipal;
import com.unipi.PlayerHive.repository.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;

/**
 * Custom implementation of Spring Security's UserDetailsService to load user data for authentication.
 */
@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public MyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads authentication details for a user based on their email.
     *
     * @param email The user's email serving as the username for login.
     * @return A UserDetails instance wrapping the loaded user.
     * @throws NoSuchElementException if the user cannot be found.
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email);

        if (user == null)
            throw new NoSuchElementException("User not found");

        return new UserPrincipal(user);
    }

    /**
     * Loads lightweight user information based on the internal user ID.
     *
     * @param id The ID string of the user.
     * @return The User entity.
     * @throws NoSuchElementException if the user cannot be found.
     */
    public User loadUserById(String id) {
        User user = userRepository.findByIdLean(id);

        if (user == null)
            throw new NoSuchElementException("User not found: " + id);

        return user;
    }
}