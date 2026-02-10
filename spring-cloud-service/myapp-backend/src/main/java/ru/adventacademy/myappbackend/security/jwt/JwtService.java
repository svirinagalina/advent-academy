package ru.adventacademy.myappbackend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import ru.adventacademy.myappbackend.entity.User;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    // Простой секрет на время разработки (потом можно вынести в конфиг)
    private final SecretKey signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // 1 час жизни токена
    private static final long EXPIRATION_MS = 60 * 60 * 1000;

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getPayload().getSubject();
    }

    public boolean isTokenValid(String token, User user) {
        String username = extractUsername(token);
        return username.equals(user.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractAllClaims(token).getPayload().getExpiration();
        return expiration.before(new Date());
    }

    private Jws<Claims> extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}
