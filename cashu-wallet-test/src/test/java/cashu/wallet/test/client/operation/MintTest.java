package cashu.wallet.test.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MintTest {

    @Test
    public void mint() {
        List<ProofEntity> proofs0 = new ProofClient().getAllProofs();
        Integer totalAmount0 = proofs0.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        Wallet wallet = new Wallet(PaymentMethod.MOCK);
        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(100, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);

        List<ProofEntity> proofs1 = new ProofClient().getAllProofs();
        Integer totalAmount1 = proofs1.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        assertTrue(proofs0.size() < proofs1.size());
        assertEquals(100, totalAmount1 - totalAmount0);

    }
}
