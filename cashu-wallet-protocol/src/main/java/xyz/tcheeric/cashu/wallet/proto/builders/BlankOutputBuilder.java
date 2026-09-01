package xyz.tcheeric.cashu.wallet.proto.builders;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.entities.annotation.Nut;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the NUT-08 blank outputs a wallet sends with a melt request so the mint can
 * return the unspent part of the Lightning fee reserve.
 *
 * <p>A blank output is an ordinary blinded message whose amount is left at zero. The
 * mint imprints the real amount before signing. Without them, any fee reserve the mint
 * does not consume is forfeited by the wallet.
 *
 * <p>The number of blank outputs is {@code max(ceil(log2(fee_reserve)), 1)}, and zero
 * when the fee reserve is zero. That count is enough to express any change amount as a
 * sum of distinct powers of two.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/08.md">NUT-08: Lightning fee return</a>
 */
@Nut(8)
@Slf4j
public class BlankOutputBuilder {

    /** Blank outputs carry no amount; the mint imprints one before signing. */
    private static final int BLANK_OUTPUT_AMOUNT = 0;

    /** A non-zero fee reserve always warrants at least one blank output. */
    private static final int MINIMUM_BLANK_OUTPUTS = 1;

    /** Upper bound on blank outputs, guarding against an absurd fee reserve. */
    static final int MAX_BLANK_OUTPUTS = 64;

    /**
     * Calculates how many blank outputs a fee reserve requires, per NUT-08.
     *
     * @param feeReserve the fee reserve quoted by the mint, in the keyset unit
     * @return {@code 0} when the reserve is zero, otherwise {@code max(ceil(log2(feeReserve)), 1)}
     * @throws IllegalArgumentException when the fee reserve is negative
     */
    public int calculateBlankOutputCount(int feeReserve) {
        if (feeReserve < 0) {
            throw new IllegalArgumentException("Fee reserve cannot be negative, got: " + feeReserve);
        }

        if (feeReserve == 0) {
            return 0;
        }

        int bitLength = ceilLog2(feeReserve);
        return Math.min(Math.max(bitLength, MINIMUM_BLANK_OUTPUTS), MAX_BLANK_OUTPUTS);
    }

    /**
     * Creates blank outputs from deterministic secrets and their blinding factors.
     *
     * <p>The caller derives exactly {@link #calculateBlankOutputCount(int)} secrets so that
     * the returned change can be unblinded, and recovered later from the mnemonic.
     *
     * @param secrets deterministic secrets, one per blank output
     * @param blindingFactors blinding factors matching the secrets, 32 bytes each
     * @return blinded messages with amount zero, in the order the secrets were supplied
     * @throws IllegalArgumentException when the lists disagree in size or content
     */
    public List<BlindedMessage> createBlankOutputs(
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors) {

        if (secrets.size() != blindingFactors.size()) {
            throw new IllegalArgumentException(String.format(
                "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                secrets.size(), blindingFactors.size()
            ));
        }

        List<BlindedMessage> blankOutputs = new ArrayList<>(secrets.size());
        for (int index = 0; index < secrets.size(); index++) {
            blankOutputs.add(createBlankOutput(secrets.get(index), blindingFactors.get(index), index));
        }

        log.info("blank_output_builder outputs_created count={}", blankOutputs.size());
        return blankOutputs;
    }

    private BlindedMessage createBlankOutput(DeterministicSecret secret, byte[] blindingFactor, int index) {
        requireBlindingFactor(blindingFactor, index);
        KeysetId keysetId = requireKeysetId(secret, index);

        byte[] blindedBytes = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);

        return BlindedMessage.builder()
            .amount(BLANK_OUTPUT_AMOUNT)
            .keySetId(keysetId)
            .blindedMessage(PublicKey.fromBytes(blindedBytes, false))
            .build();
    }

    private void requireBlindingFactor(byte[] blindingFactor, int index) {
        if (blindingFactor == null || blindingFactor.length != 32) {
            throw new IllegalArgumentException(String.format(
                "Blinding factor at index %d must be 32 bytes, got: %s",
                index, blindingFactor == null ? "null" : blindingFactor.length
            ));
        }
    }

    private KeysetId requireKeysetId(DeterministicSecret secret, int index) {
        KeysetId keysetId = secret.getKeysetId();
        if (keysetId == null) {
            throw new IllegalArgumentException("Secret at index " + index + " has no keyset ID");
        }
        return keysetId;
    }

    private int ceilLog2(int value) {
        int floorLog2 = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(value);
        boolean isExactPowerOfTwo = Integer.bitCount(value) == 1;
        return isExactPowerOfTwo ? floorLog2 : floorLog2 + 1;
    }
}
