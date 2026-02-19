package xyz.tcheeric.cashu.wallet.proto.builders;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.BlindedMessage;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeysetId;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut09.PostRestoreRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder utility for creating NUT-09 restore requests with deterministically derived secrets.
 *
 * <p>This builder creates {@link BlindedMessage} instances from deterministic secrets and their
 * corresponding blinding factors, then packages them into a {@link PostRestoreRequest} for
 * submission to the mint's /restore endpoint.
 *
 * <p>The blinding process uses BDHKE (Blind Diffie-Hellman Key Exchange) to create blinded
 * messages that can be signed by the mint without revealing the secret. During recovery,
 * the mint returns signatures for any blinded messages it recognizes.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // After deriving secrets and blinding factors
 * DeriveSecretsResult result = deriveSecretsTask.execute();
 *
 * // Create restore request
 * RestoreRequestBuilder builder = new RestoreRequestBuilder();
 * List<BlindedMessage> blindedMessages = builder.createBlindedMessages(
 *     result.getSecrets(),
 *     result.getBlindingFactors(),
 *     1  // amount in sats
 * );
 *
 * PostRestoreRequest request = builder.buildRequest(blindedMessages);
 * }</pre>
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/09.md">NUT-09: Restore Signatures</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 * @see PostRestoreRequest
 * @see BlindedMessage
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
@Nut(9)
@Slf4j
@AllArgsConstructor
@ToString
public class RestoreRequestBuilder {

    /**
     * Creates blinded messages from deterministic secrets and their blinding factors.
     *
     * <p>For each secret-blinding factor pair, this method:
     * <ol>
     *   <li>Uses BDHKE to blind the secret with the provided blinding factor</li>
     *   <li>Creates a {@link BlindedMessage} with the specified amount and keyset ID</li>
     *   <li>Returns the list of blinded messages ready for restore request</li>
     * </ol>
     *
     * <p>The blinding is deterministic - using the same secret and blinding factor will
     * always produce the same blinded message. This property enables wallet recovery:
     * wallets can recreate the same blinded messages from a mnemonic and the mint can
     * recognize them even after wallet loss.
     *
     * <p><strong>Requirements:</strong>
     * <ul>
     *   <li>secrets.size() must equal blindingFactors.size()</li>
     *   <li>All secrets must have the same keyset ID</li>
     *   <li>All blinding factors must be 32 bytes</li>
     *   <li>Amount must be positive</li>
     * </ul>
     *
     * @param secrets List of deterministic secrets with metadata
     * @param blindingFactors List of blinding factors (r values) corresponding to each secret
     * @param amount Token amount in satoshis (must be positive)
     * @return List of blinded messages ready for submission to mint
     * @throws IllegalArgumentException if lists have different sizes, keyset IDs differ, or amount is invalid
     * @throws IllegalStateException if blinding operation fails
     */
    public List<BlindedMessage> createBlindedMessages(
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors,
            int amount) {

        // Validate inputs
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("Secrets list cannot be empty");
        }

        if (secrets.size() != blindingFactors.size()) {
            throw new IllegalArgumentException(String.format(
                "Secrets and blinding factors count mismatch: secrets=%d, blindingFactors=%d",
                secrets.size(), blindingFactors.size()
            ));
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }

        // Verify all secrets have the same keyset ID
        KeysetId keysetId = secrets.get(0).getKeysetId();
        if (keysetId == null) {
            throw new IllegalArgumentException("First secret has null keyset ID");
        }

        for (int i = 1; i < secrets.size(); i++) {
            KeysetId currentKeysetId = secrets.get(i).getKeysetId();
            if (currentKeysetId == null || !currentKeysetId.equals(keysetId)) {
                throw new IllegalArgumentException(String.format(
                    "All secrets must have the same keyset ID. Expected=%s, got=%s at index=%d",
                    keysetId, currentKeysetId, i
                ));
            }
        }

        log.info("restore_request_builder blinding_started keyset={} secret_count={} amount={}",
            keysetId, secrets.size(), amount);

        List<BlindedMessage> blindedMessages = new ArrayList<>(secrets.size());

        for (int i = 0; i < secrets.size(); i++) {
            DeterministicSecret secret = secrets.get(i);
            byte[] blindingFactor = blindingFactors.get(i);

            try {
                // Validate blinding factor
                if (blindingFactor == null || blindingFactor.length != 32) {
                    throw new IllegalArgumentException(String.format(
                        "Blinding factor at index %d must be 32 bytes, got: %s",
                        i, blindingFactor == null ? "null" : blindingFactor.length
                    ));
                }

                // Blind the secret using BDHKE with the provided blinding factor
                byte[] blindedBytes = BDHKEUtils.blindMessage(secret.getData(), blindingFactor);

                // Create blinded message
                BlindedMessage blindedMessage = BlindedMessage.builder()
                    .amount(amount)
                    .keySetId(keysetId)
                    .blindedMessage(PublicKey.fromBytes(blindedBytes, false))
                    .build();

                blindedMessages.add(blindedMessage);

            } catch (Exception e) {
                log.error("restore_request_builder blinding_failed index={} counter={} error={} impact=abort_batch",
                    i, secret.getCounter(), e.getMessage(), e);
                throw new IllegalStateException(
                    String.format("Failed to create blinded message at index %d (counter=%d): %s",
                        i, secret.getCounter(), e.getMessage()),
                    e
                );
            }
        }

        log.info("restore_request_builder blinding_completed keyset={} created_count={}",
            keysetId, blindedMessages.size());

        return blindedMessages;
    }

    /**
     * Builds a {@link PostRestoreRequest} from blinded messages.
     *
     * <p>This is a simple packaging method that wraps the blinded messages into the
     * REST request DTO expected by the mint's /restore endpoint.
     *
     * <p>The request can be submitted to the mint via the {@code RequestRestore} client.
     *
     * @param blindedMessages List of blinded messages to include in the request
     * @return PostRestoreRequest ready for submission to mint
     * @throws IllegalArgumentException if blindedMessages is null or empty
     */
    public PostRestoreRequest buildRequest(@NonNull List<BlindedMessage> blindedMessages) {
        if (blindedMessages.isEmpty()) {
            throw new IllegalArgumentException("Blinded messages list cannot be empty");
        }

        log.debug("restore_request_builder request_build_started outputs_count={}", blindedMessages.size());

        return new PostRestoreRequest(blindedMessages);
    }

    /**
     * Convenience method that creates blinded messages and builds the request in one call.
     *
     * <p>Equivalent to calling {@link #createBlindedMessages(List, List, int)} followed by
     * {@link #buildRequest(List)}.
     *
     * @param secrets List of deterministic secrets
     * @param blindingFactors List of blinding factors
     * @param amount Token amount in satoshis
     * @return PostRestoreRequest ready for submission to mint
     * @throws IllegalArgumentException if inputs are invalid
     * @throws IllegalStateException if blinding operation fails
     */
    public PostRestoreRequest buildRequestFromSecrets(
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors,
            int amount) {

        List<BlindedMessage> blindedMessages = createBlindedMessages(secrets, blindingFactors, amount);
        return buildRequest(blindedMessages);
    }
}
