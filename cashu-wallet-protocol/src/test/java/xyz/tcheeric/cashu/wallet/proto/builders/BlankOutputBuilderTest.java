package xyz.tcheeric.cashu.wallet.proto.builders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BlankOutputBuilder}, covering the NUT-08 blank output count and shape.
 */
class BlankOutputBuilderTest {

    private static final KeysetId KEYSET_ID = KeysetId.fromString("009a1f293253e41e");

    private BlankOutputBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new BlankOutputBuilder();
    }

    /**
     * Ensures the blank output count matches the NUT-08 formula max(ceil(log2(fee_reserve)), 1),
     * including the worked example from the spec where a 1000 sat reserve needs 10 outputs.
     */
    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "1, 1",
        "2, 1",
        "3, 2",
        "4, 2",
        "5, 3",
        "1000, 10",
        "1024, 10"
    })
    void shouldMatchSpecFormulaWhenCalculatingBlankOutputCount(int feeReserve, int expectedCount) {
        assertThat(builder.calculateBlankOutputCount(feeReserve)).isEqualTo(expectedCount);
    }

    /**
     * Ensures a negative fee reserve is rejected rather than silently producing no outputs.
     */
    @Test
    void shouldRejectNegativeFeeReserveWhenCalculatingBlankOutputCount() {
        assertThatThrownBy(() -> builder.calculateBlankOutputCount(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("-1");
    }

    /**
     * Ensures each blank output carries amount zero, as NUT-08 requires the mint to imprint it.
     */
    @Test
    void shouldCreateZeroAmountOutputsWhenBlindingBlankOutputs() {
        List<DeterministicSecret> secrets = List.of(secret(1), secret(2));
        List<byte[]> blindingFactors = List.of(blindingFactor((byte) 1), blindingFactor((byte) 2));

        List<BlindedMessage> blankOutputs = builder.createBlankOutputs(secrets, blindingFactors);

        assertThat(blankOutputs).hasSize(2);
        assertThat(blankOutputs).allSatisfy(output -> {
            assertThat(output.getAmount()).isZero();
            assertThat(output.getKeySetId()).isEqualTo(KEYSET_ID);
            assertThat(output.getBlindedMessage()).isNotNull();
        });
    }

    /**
     * Ensures mismatched secret and blinding factor lists fail rather than producing
     * outputs the wallet could never unblind.
     */
    @Test
    void shouldRejectMismatchedInputsWhenBlindingBlankOutputs() {
        assertThatThrownBy(() -> builder.createBlankOutputs(
            List.of(secret(1)), List.of(blindingFactor((byte) 1), blindingFactor((byte) 2))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mismatch");
    }

    /**
     * Ensures an undersized blinding factor is rejected, since BDHKE requires 32 bytes.
     */
    @Test
    void shouldRejectShortBlindingFactorWhenBlindingBlankOutputs() {
        assertThatThrownBy(() -> builder.createBlankOutputs(
            List.of(secret(1)), List.of(new byte[16])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }

    private DeterministicSecret secret(int counter) {
        byte[] data = new byte[32];
        data[31] = (byte) counter;
        return DeterministicSecret.create(data, KEYSET_ID, counter);
    }

    private byte[] blindingFactor(byte seed) {
        byte[] factor = new byte[32];
        factor[31] = seed;
        return factor;
    }
}
