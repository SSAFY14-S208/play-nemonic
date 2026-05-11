package com.nemonicworld.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nemonicworld.common.exception.RoomCodeGenerationException;
import java.util.Arrays;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    private static final String ALLOWED_ROOM_CODE_PATTERN = "^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$";

    @Test
    void generateReturnsSixCharacterHumanReadableCode() {
        RoomCodeGenerator roomCodeGenerator = new RoomCodeGenerator();

        String roomCode = roomCodeGenerator.generate();

        assertThat(roomCode).matches(ALLOWED_ROOM_CODE_PATTERN);
    }

    @Test
    void generatedCodeDoesNotContainAmbiguousCharacters() {
        RoomCodeGenerator roomCodeGenerator = new RoomCodeGenerator();

        for (int sampleIndex = 0; sampleIndex < 200; sampleIndex++) {
            String roomCode = roomCodeGenerator.generate();

            assertThat(roomCode).doesNotContain("0", "1", "I", "L", "O");
        }
    }

    @Test
    void generateUniqueSkipsExistingCode() {
        RoomCodeGenerator roomCodeGenerator = new RoomCodeGenerator(
            new SequenceRandomGenerator(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1));

        String roomCode = roomCodeGenerator.generateUnique(existingCode -> existingCode.equals("AAAAAA"));

        assertThat(roomCode).isEqualTo("BBBBBB");
    }

    @Test
    void generateUniqueThrowsWhenEveryAttemptCollides() {
        int[] alwaysZeroIndexes = new int[120];
        Arrays.fill(alwaysZeroIndexes, 0);
        RoomCodeGenerator roomCodeGenerator = new RoomCodeGenerator(new SequenceRandomGenerator(alwaysZeroIndexes));

        assertThatThrownBy(() -> roomCodeGenerator.generateUnique(existingCode -> true))
            .isInstanceOf(RoomCodeGenerationException.class).hasMessage("방코드 생성에 실패했습니다.");
    }

    @Test
    void isValidAcceptsOnlyGeneratedCodeShape() {
        RoomCodeGenerator roomCodeGenerator = new RoomCodeGenerator();

        assertThat(roomCodeGenerator.isValid("ABCD23")).isTrue();
        assertThat(roomCodeGenerator.isValid(null)).isFalse();
        assertThat(roomCodeGenerator.isValid("ABCD2")).isFalse();
        assertThat(roomCodeGenerator.isValid("ABCD234")).isFalse();
        assertThat(roomCodeGenerator.isValid("abcd23")).isFalse();
        assertThat(roomCodeGenerator.isValid("ABC123")).isFalse();
        assertThat(roomCodeGenerator.isValid("ABCI23")).isFalse();
        assertThat(roomCodeGenerator.isValid("ABCL23")).isFalse();
        assertThat(roomCodeGenerator.isValid("ABCO23")).isFalse();
    }

    private static final class SequenceRandomGenerator implements RandomGenerator {

        private final int[] indexes;
        private int nextIndex;

        SequenceRandomGenerator(int... indexes) {
            this.indexes = indexes;
        }

        @Override
        public int nextInt(int bound) {
            int selectedIndex = indexes[nextIndex];
            nextIndex++;
            return selectedIndex;
        }

        @Override
        public long nextLong() {
            return nextInt(Integer.MAX_VALUE);
        }
    }
}
