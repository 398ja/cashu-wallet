package xyz.tcheeric.cashu.wallet.proto.service;

import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.nut13.DeterministicSecret;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltResponse;

import java.util.List;

/**
 * Turns the NUT-08 change a mint returns after a melt into spendable proofs.
 *
 * <p>The mint imprints amounts on the blank outputs it received, signs them, and returns
 * the non-zero signatures in the order the blank outputs were sent. This service matches
 * those signatures back to the wallet's secrets and unblinds them.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/08.md">NUT-08: Lightning fee return</a>
 */
public interface MeltChangeService {

    /**
     * Unblinds the change signatures returned by a melt into spendable proofs.
     *
     * @param response the melt response, whose change may be absent or empty
     * @param secrets the deterministic secrets behind the blank outputs, in send order
     * @param blindingFactors the blinding factors matching those secrets
     * @param keySet the mint keyset providing public keys per amount
     * @return recovered change proofs, empty when the mint returned no change
     * @throws IllegalArgumentException when secrets and blinding factors disagree in size,
     *         or the mint returned more change than blank outputs were sent
     */
    List<Proof<DeterministicSecret>> recoverChange(
            PostMeltResponse response,
            List<DeterministicSecret> secrets,
            List<byte[]> blindingFactors,
            KeySet keySet
    );
}
