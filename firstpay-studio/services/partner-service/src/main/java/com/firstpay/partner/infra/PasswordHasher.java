package com.firstpay.partner.infra;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Hachage et vérification de mots de passe (BCrypt). Génère aussi les mots de passe temporaires. */
@Component
public class PasswordHasher {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789abcdefghijkmnpqrstuvwxyz";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String hash(String raw) { return encoder.encode(raw); }

    public boolean matches(String raw, String hash) {
        return hash != null && !hash.isBlank() && encoder.matches(raw, hash);
    }

    /** Mot de passe temporaire lisible (12 caractères). */
    public String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
