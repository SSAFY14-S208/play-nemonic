package com.nemonicworld.common.util;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int MAX_GENERATION_ATTEMPTS = 20;
    private static final String GENERATION_FAILURE_MESSAGE = "방코드 생성에 실패했습니다.";

    private final RandomGenerator randomGenerator;

    public RoomCodeGenerator() {
        this(new SecureRandom());
    }

    RoomCodeGenerator(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator);
    }

    public String generate() {
        StringBuilder roomCode = new StringBuilder(ROOM_CODE_LENGTH);

        for (int position = 0; position < ROOM_CODE_LENGTH; position++) {
            int randomIndex = randomGenerator.nextInt(ALPHABET.length());
            roomCode.append(ALPHABET.charAt(randomIndex));
        }

        return roomCode.toString();
    }

    public String generateUnique(Predicate<String> existingCodePredicate) {
        Objects.requireNonNull(existingCodePredicate);

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String roomCode = generate();

            if (!existingCodePredicate.test(roomCode)) {
                return roomCode;
            }
        }

        throw new IllegalStateException(GENERATION_FAILURE_MESSAGE);
    }

    public boolean isValid(String roomCode) {
        if (roomCode == null || roomCode.length() != ROOM_CODE_LENGTH) {
            return false;
        }

        for (int position = 0; position < roomCode.length(); position++) {
            if (ALPHABET.indexOf(roomCode.charAt(position)) < 0) {
                return false;
            }
        }

        return true;
    }
}
