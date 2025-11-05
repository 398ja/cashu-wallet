package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

/**
 * REST client for the NUT-09 restore endpoint.
 *
 * <p>This client submits blinded messages to the mint's /restore endpoint to recover
 * signatures for previously minted tokens. This is the core mechanism for wallet recovery
 * in NUT-13 deterministic wallets.
 *
 * <p>The restore process works as follows:
 * <ol>
 *   <li>Wallet derives deterministic secrets and blinding factors from mnemonic</li>
 *   <li>Wallet creates blinded messages using {@link xyz.tcheeric.cashu.wallet.proto.builders.RestoreRequestBuilder}</li>
 *   <li>Wallet submits blinded messages to mint via this RequestRestore client</li>
 *   <li>Mint returns signatures for any recognized blinded messages</li>
 *   <li>Wallet unblinds signatures to recover {@link xyz.tcheeric.cashu.common.Proof} objects</li>
 * </ol>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Create restore request with blinded messages
 * RestoreRequestBuilder builder = new RestoreRequestBuilder();
 * PostRestoreRequest request = builder.buildRequestFromSecrets(
 *     secrets,
 *     blindingFactors,
 *     amount
 * );
 *
 * // Submit to mint
 * RequestRestore restoreClient = new RequestRestore(mintUrl, request);
 * PostRestoreResponse response = restoreClient.execute();
 *
 * // Process returned signatures
 * if (!response.getBlindSignatures().isEmpty()) {
 *     // Mint found matching tokens - unblind and recover proofs
 * }
 * }</pre>
 *
 * <p><strong>Batch Recovery:</strong> It's recommended to batch restore requests in groups
 * of 100 tokens (per NUT-13 spec) and continue until 3 consecutive empty batches are
 * returned by the mint.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/09.md">NUT-09: Restore Signatures</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 * @see PostRestoreRequest
 * @see PostRestoreResponse
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
@Nut(9)
public class RequestRestore extends AbstractRequestBase<PostRestoreResponse, PostRestoreRequest> {

    /**
     * Creates a new RequestRestore client for wallet recovery.
     *
     * @param baseUrl Mint base URL (e.g., "https://mint.example.com")
     * @param postRestoreRequest Request containing blinded messages to restore
     * @throws NullPointerException if baseUrl or postRestoreRequest is null
     */
    public RequestRestore(@NonNull String baseUrl, @NonNull PostRestoreRequest postRestoreRequest) {
        super(baseUrl, "/restore", HTTP_METHOD_POST, postRestoreRequest, PostRestoreResponse.class);
    }
}
