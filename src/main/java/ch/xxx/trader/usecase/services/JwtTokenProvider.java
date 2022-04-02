/**
 *    Copyright 2016 Sven Loesekann

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package ch.xxx.trader.usecase.services;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import ch.xxx.trader.domain.common.JwtUtils;
import ch.xxx.trader.domain.common.Role;
import ch.xxx.trader.domain.exceptions.AuthenticationException;
import ch.xxx.trader.domain.exceptions.JwtTokenValidationException;
import ch.xxx.trader.domain.model.entity.MyUser;
import ch.xxx.trader.domain.model.entity.RevokedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	@Value("${security.jwt.token.secret-key}")
	private String secretKey;

	@Value("${security.jwt.token.expire-length}")
	private long validityInMilliseconds; // 24h

	private final ReactiveMongoOperations operations;
	
	private final List<UserNameUuid> loggedOutUsers = new CopyOnWriteArrayList<>();
	
	public record UserNameUuid(String userName, String uuid) {}
	
	public JwtTokenProvider(ReactiveMongoOperations operations) {
		this.operations = operations;
	}

	public void updateLoggedOutUsers(List<RevokedToken> users) {
		this.loggedOutUsers.clear();
		this.loggedOutUsers
				.addAll(users.stream().map(myUser -> new UserNameUuid(myUser.getName(), myUser.getUuid())).toList());
	}
	
	public String createToken(String username, List<Role> roles) {
		Claims claims = Jwts.claims().setSubject(username);
		claims.put("auth", roles.stream().map(s -> new SimpleGrantedAuthority(s.getAuthority()))
				.filter(Objects::nonNull).collect(Collectors.toList()));

		Date now = new Date();
		Date validity = new Date(now.getTime() + validityInMilliseconds);
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());

		return Jwts.builder().setClaims(claims).setIssuedAt(now).setExpiration(validity)
				.signWith(key, SignatureAlgorithm.HS256).compact();
	}

	public Optional<Jws<Claims>> getClaims(Optional<String> token) {
		if (!token.isPresent()) {
			return Optional.empty();
		}
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
		return Optional.of(Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token.get()));
	}

	public UsernamePasswordAuthenticationToken getUserAuthenticationToken(String token) {
		Query query = new Query();
		query.addCriteria(Criteria.where("userId").is(getUsername(token)));
		MyUser user = operations.findOne(query, MyUser.class).block();

		return new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities());
	}

	public String getUsername(String token) {
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
	}

	public String getUuid(String token) {
		this.validateToken(token);
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().get(JwtUtils.UUID, String.class);
	}
	
	public String refreshToken(String token) {
		validateToken(token);
		Optional<Jws<Claims>> claimsOpt = this.getClaims(Optional.of(token));
		if (claimsOpt.isEmpty()) {
			throw new AuthenticationException("Invalid token claims");
		}
		Claims claims = claimsOpt.get().getBody();
		claims.setIssuedAt(new Date());
		claims.setExpiration(new Date(Instant.now().toEpochMilli() + validityInMilliseconds));
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
		String newToken = Jwts.builder().setClaims(claims).signWith(key, SignatureAlgorithm.HS256)
				.compact();
		return newToken;
	}
	
	public String resolveToken(HttpServletRequest req) {
		String bearerToken = req.getHeader("Authorization");
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7, bearerToken.length());
		}
		return null;
	}

	public Optional<String> resolveToken(String bearerToken) {
		if (bearerToken != null && bearerToken.startsWith(JwtUtils.BEARER)) {
			return Optional.of(bearerToken.substring(7, bearerToken.length()));
		}
		return Optional.empty();
	}
	
	public boolean validateToken(String token) {
		SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
		try {
			Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			throw new JwtTokenValidationException("Expired or invalid JWT token", e);
		}
	}

}
