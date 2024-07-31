package cashu.wallet.test.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.client.util.WalletUtil;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import lombok.extern.java.Log;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Log
public class SwapTest {


    @Test
    public void swap() {
        Wallet wallet = new Wallet(PaymentMethod.MOCK);
        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(10, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);

        List<ProofEntity> proofs0 = new ProofClient().getAllProofs();
        Integer totalAmount0 = proofs0.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        ProofEntity proofEntity = new ProofClient().getByAmountAndKeysetId(8, WalletUtil.getKeysetId("sat"));
        wallet.swapTokens(proofEntity, Map.of(4, 2), "sat");

        List<ProofEntity> proofs1 = new ProofClient().getAllProofs();
        Integer totalAmount1 = proofs1.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        assertEquals(totalAmount0, totalAmount1);

        // TODO - Ensure that proofs0 and proofs1 are different
        // TODO - Ensure the proofs0 were deleted from the database
    }
}
