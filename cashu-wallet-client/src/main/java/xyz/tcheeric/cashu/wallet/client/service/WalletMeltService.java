package xyz.tcheeric.cashu.wallet.client.service;

import org.bitcoinj.crypto.DeterministicKey;
import xyz.tcheeric.cashu.common.KeySet;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteResponse;

import java.util.List;

/**
 * Melts proofs against a mint quote and recovers the unspent Lightning fee reserve.
 *
 * <p>Per NUT-08 the wallet sends {@code max(ceil(log2(fee_reserve)), 1)} blank outputs
 * with the melt request. When the actual routing fee is below the reserve, the mint
 * imprints the difference on those outputs and returns signatures the wallet unblinds
 * into spendable proofs. A wallet that omits the blank outputs forfeits that difference.
 *
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/05.md">NUT-05: Melting tokens</a>
 * @see <a href="https://github.com/cashubtc/nuts/blob/main/08.md">NUT-08: Lightning fee return</a>
 */
public interface WalletMeltService {

    /**
     * Melts the supplied proofs against a melt quote, returning any change recovered.
     *
     * @param quote the melt quote naming the quote ID and the fee reserve
     * @param inputs the proofs funding the payment
     * @param masterKey the BIP32 master key used to derive blank output secrets (NUT-13)
     * @param keySet the active mint keyset for the blank outputs and change
     * @param startCounter the NUT-13 counter to derive blank output secrets from
     * @param paymentMethod the melt payment method
     * @return the melt outcome, including unblinded change proofs and the next counter
     */
    <T extends Secret> MeltResult melt(
            PostMeltQuoteResponse quote,
            List<Proof<T>> inputs,
            DeterministicKey masterKey,
            KeySet keySet,
            int startCounter,
            PaymentMethod paymentMethod
    );
}
