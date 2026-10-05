package com.jogodavelha.auth;

import com.jogodavelha.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Service responsável pela geração e validação de tokens JWT.
 * <p>
 * A validação de tokens em requisições será implementada na BE-005 (filtro JWT).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public JwtService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    /**
     * Gera um token JWT para o usuário fornecido.
     *
     * @param user o usuário para o qual o token será gerado
     * @return o token JWT assinado
     */
    public String generateToken(User user) {
        Instant now = clock.instant();
        Instant expiration = now.plusMillis(jwtProperties.expiration());

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
}
