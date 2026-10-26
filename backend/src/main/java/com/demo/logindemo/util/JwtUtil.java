package com.demo.logindemo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {
    // 密钥，长度至少32位
    private static final String SECRET = requireSecret();
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    // 有效期 2小时
    private static final long EXPIRATION = 2 * 60 * 60 * 1000L;

    private static String requireSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return secret;
    }

    public static String generateToken(Long userId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRATION);
        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(KEY)
                .parseClaimsJws(token)
                .getBody();
    }
    public static Long getUserId(String token){
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }
    
}