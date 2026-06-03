package com.unipi.PlayerHive.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

/**
 * Utility class for generating, parsing, and validating JWT tokens.
 */
public class JwtUtils {

    private static final String secretKey;
    private static final long JWT_TOKEN_VALIDITY = 2 * 60 * 60 * 1000; // 2 hours in ms

    static {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            SecretKey sk = keyGen.generateKey();
            secretKey = Base64.getEncoder().encodeToString(sk.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private JwtUtils() {}

    /**
     * Generates a new JWT token for a specific subject (user ID).
     *
     * @param sub The subject (user ID) to encode in the token.
     * @return The generated JWT token string.
     */
    public static String generateToken(String sub) {
        return Jwts.builder()
                .setSubject(sub)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates a given JWT token by ensuring it can be parsed and is not expired.
     *
     * @param token The JWT token to validate.
     * @return True if the token is valid and not expired, false otherwise.
     */
    public static boolean validateToken(String token) {
        try {
            extractUserId(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the user ID (subject) from a given JWT token.
     *
     * @param token The JWT token.
     * @return The extracted user ID string.
     */
    public static String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generates the HMAC secret key used for signing and verifying tokens.
     *
     * @return The SecretKey instance.
     */
    private static SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Extracts a specific claim from a JWT token using a provided resolver function.
     *
     * @param token         The JWT token.
     * @param claimResolver The function to resolve the desired claim.
     * @param <T>           The type of the claim being extracted.
     * @return The extracted claim.
     */
    private static <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimResolver.apply(claims);
    }

    /**
     * Checks if a given JWT token has expired.
     *
     * @param token The JWT token to check.
     * @return True if the token's expiration date is before the current time, false otherwise.
     */
    private static boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}