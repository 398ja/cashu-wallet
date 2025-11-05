package xyz.tcheeric.cashu.wallet.proto.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.DeterministicSecret;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.entities.rest.PostRestoreResponse;

import java.util.List;

/**
 * Service for recovering proofs from blind signatures during wallet recovery.
 *
 * <p>This service handles the unblinding of signatures returned by the mint during the
 * restore process (NUT-09). It takes blind signatures from the mint and combines them
 * with the wallet's blinding factors to create {@link Proof} objects that can be spent.
 *
 * <p>The recovery process involves:
 * <ol>
 *   <li>Matching returned blind signatures with their corresponding secrets and blinding factors</li>
 *   <li>Unblinding each signature using the blinding factor (r) and mint's public key</li>
 *   <li>Creating Proof objects with the unblinded signatures</li>
 *   <li>Optionally verifying proofs are unspent (using NUT-07)</li>
 * </ol>
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/09.md">NUT-09: Restore Signatures</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/13.md">NUT-13: Deterministic Secrets</a>
 *
 * @author NUT-13 Implementation Team
 * @since 1.0.0
 */
public interface ProofRecoveryService {

    /**
     * Unblinds signatures and creates proofs from restore response.
     *
     * <p>This method processes the blind signatures returned by the mint's /restore endpoint
     * and combines them with the wallet's deterministic secrets and blinding factors to
     * create valid {@link Proof} objects.
     *
     * <p>The method assumes a one-to-one correspondence between the lists:
     * {@code secrets.get(i)} and {@code blindingFactors.get(i)} were used to create
     * the blinded message that resulted in {@code response.getBlindSignatures().get(i)}.
     *
     * <p><strong>Important:</strong> The restore response may contain fewer signatures than
     * the number of secrets sent. Only tokens that were previously minted will have
     * signatures in the response.
     *
     * @param response Restore response from mint containing blind signatures
     * @param secrets Deterministic secrets used to create the blinded messages
     * @param blindingFactors Blinding factors (r values) used to create the blinded messages
     * @param keySet Mint's keyset containing public keys for unblinding
     * @return List of recovered proofs ready to be spent
     * @throws IllegalArgumentException if secrets and blindingFactors have different sizes
     * @throws IllegalStateException if unblinding fails
     */
    List<Proof<DeterministicSecret>> unblindAndCreateProofs(
            @NonNull PostRestoreResponse response,
            @NonNull List<DeterministicSecret> secrets,
            @NonNull List<byte[]> blindingFactors,
            @NonNull KeySet keySet
    );

    /**
     * Verifies that recovered proofs are unspent.
     *
     * <p>This optional method checks each proof against the mint using NUT-07 to determine
     * if it has already been spent. This is useful for filtering out proofs that were
     * recovered but are no longer spendable.
     *
     * <p>This method is optional because not all recovery scenarios require spent-checking.
     * For example, during initial recovery, all proofs are assumed to be potentially spendable.
     *
     * @param proofs List of proofs to verify
     * @param mintUrl Mint base URL for spent-check requests
     * @return List of unspent proofs (subset of input proofs)
     * @throws IllegalStateException if verification fails
     */
    List<Proof<DeterministicSecret>> filterUnspentProofs(
            @NonNull List<Proof<DeterministicSecret>> proofs,
            @NonNull String mintUrl
    );
}
