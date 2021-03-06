package com.hillel.items_exchange.security.jwt;

import com.hillel.items_exchange.model.Role;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EMPTY_TOKEN = "";
    private final UserDetailsService userDetailsService;
    private final MessageSource messageSource;
    @Value("${app.jwt.secret}")
    private String secret;
    @Value("${app.jwt.expiration.time.ms}")
    private long jwtTokenExpireTime;

    @PostConstruct
    protected void init() {
        secret = Base64.getEncoder().encodeToString(secret.getBytes());
    }

    public String createToken(String username, Role role) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("role", role.getName());

        Date now = new Date();
        Date validity = new Date(now.getTime() + jwtTokenExpireTime);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(getUsername(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    private String getUsername(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody().getSubject();
    }

    public String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader(AUTHORIZATION_HEADER_NAME);
        if(bearerToken != null) {
            if(bearerToken.startsWith(BEARER_PREFIX)) {
                return bearerToken.substring(BEARER_PREFIX.length());
            } else {
                String errorMessageTokenNotStartWithBearerPrefix =
                        messageSource.getMessage("token.not.start.with.bearer", null, Locale.getDefault());
                log.error("Unauthorized: {}", errorMessageTokenNotStartWithBearerPrefix);
                req.setAttribute("detailedError", errorMessageTokenNotStartWithBearerPrefix);
                return EMPTY_TOKEN;
            }
        }
        return EMPTY_TOKEN;
    }

    public boolean validateToken(String token, HttpServletRequest req) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (JwtException e) {
            log.error("Unauthorized: {}", e.getMessage());
            req.setAttribute("detailedError", e.getMessage());
            return false;
        }
    }
}
