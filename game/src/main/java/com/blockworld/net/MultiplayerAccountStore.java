package com.blockworld.net;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Password-derived account verifier store with fresh challenge-response authentication. */
public final class MultiplayerAccountStore {
    public static final int PBKDF2_ITERATIONS = 210_000;
    private static final String HEADER = "BLOCKWORLD_SERVER_ACCOUNTS";
    private static final int FORMAT = 1;
    private final Path file;
    private final Map<String, Account> accounts = new HashMap<>();

    public MultiplayerAccountStore(Path hostedWorldFile) {
        Path world = Objects.requireNonNull(hostedWorldFile, "hostedWorldFile").toAbsolutePath().normalize();
        file = world.resolveSibling(world.getFileName() + ".accounts");
    }

    public synchronized void load() throws IOException {
        accounts.clear();
        if (!Files.isRegularFile(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine()) || !("format=" + FORMAT).equals(reader.readLine()))
                throw new IOException("Invalid account database");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] fields = line.split(" ");
                if (fields.length != 4 || !"A".equals(fields[0])) throw new IOException("Invalid account row");
                try {
                    String username = decode(fields[1]);
                    byte[] salt = Base64.getUrlDecoder().decode(fields[2]);
                    byte[] verifier = Base64.getUrlDecoder().decode(fields[3]);
                    if (username.isBlank() || salt.length != 16 || verifier.length != 32
                            || accounts.putIfAbsent(key(username), new Account(username, salt, verifier)) != null)
                        throw new IOException("Invalid or duplicate account");
                } catch (IllegalArgumentException error) {
                    throw new IOException("Invalid account encoding", error);
                }
            }
        }
    }

    public synchronized boolean exists(String username) {
        return accounts.containsKey(key(username));
    }

    public synchronized byte[] saltFor(String username) {
        Account account = accounts.get(key(username));
        return account == null ? null : account.salt.clone();
    }

    public synchronized boolean register(String username, byte[] salt, byte[] verifier,
                                         byte[] nonce, byte[] proof) throws IOException {
        String accountKey = key(username);
        if (accounts.containsKey(accountKey) || salt == null || salt.length != 16
                || verifier == null || verifier.length != 32
                || !validProof(verifier, nonce, proof)) return false;
        accounts.put(accountKey, new Account(username, salt, verifier));
        try {
            save();
            return true;
        } catch (IOException error) {
            accounts.remove(accountKey);
            throw error;
        }
    }

    public synchronized boolean authenticate(String username, byte[] nonce, byte[] proof) {
        Account account = accounts.get(key(username));
        return account != null && validProof(account.verifier, nonce, proof);
    }

    public static byte[] deriveVerifier(char[] password, byte[] salt) {
        if (password == null || password.length < 8 || password.length > 128)
            throw new IllegalArgumentException("Пароль аккаунта должен содержать 8–128 символов");
        if (salt == null || salt.length != 16) throw new IllegalArgumentException("Invalid account salt");
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("PBKDF2 is unavailable", error);
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] proof(byte[] verifier, byte[] nonce) {
        if (verifier == null || verifier.length != 32 || nonce == null || nonce.length != 32)
            throw new IllegalArgumentException("Invalid account challenge");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(verifier, "HmacSHA256"));
            mac.update("BLOCKWORLD-ACCOUNT-V1".getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(nonce);
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("HMAC is unavailable", error);
        }
    }

    private static boolean validProof(byte[] verifier, byte[] nonce, byte[] proof) {
        if (nonce == null || nonce.length != 32 || proof == null || proof.length != 32) return false;
        return MessageDigest.isEqual(proof(verifier, nonce), proof);
    }

    private synchronized void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(HEADER); writer.newLine();
                writer.write("format=" + FORMAT); writer.newLine();
                for (Account account : accounts.values()) {
                    writer.write("A " + encode(account.username) + " "
                            + Base64.getUrlEncoder().withoutPadding().encodeToString(account.salt) + " "
                            + Base64.getUrlEncoder().withoutPadding().encodeToString(account.verifier));
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String key(String username) {
        String value = username == null ? "" : username.strip();
        if (!value.matches("[A-Za-zА-Яа-я0-9_-]{1,20}")) throw new IllegalArgumentException("Invalid account name");
        return value.toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private record Account(String username, byte[] salt, byte[] verifier) {
        private Account {
            salt = salt.clone();
            verifier = verifier.clone();
        }
    }
}
