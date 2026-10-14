package dev.erenyasar.jobtracker.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    public record Credentials(@Email @NotBlank String email, @NotBlank @Size(min = 8, max = 72) String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody Credentials c) {
        if (users.existsByEmailIgnoreCase(c.email()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        User user = users.save(new User(c.email().toLowerCase(), passwords.encode(c.password())));
        return token(user);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody Credentials c) {
        // Same error for unknown email and wrong password, so the endpoint does not reveal which emails exist.
        User user = users.findByEmailIgnoreCase(c.email())
                .filter(u -> passwords.matches(c.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password"));
        return token(user);
    }

    private TokenResponse token(User user) {
        return new TokenResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds());
    }
}
