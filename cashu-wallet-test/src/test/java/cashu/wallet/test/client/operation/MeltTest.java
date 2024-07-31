package cashu.wallet.test.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.client.MeltQuoteRequestClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MeltQuoteRequestEntity;
import cashu.wallet.db.model.MeltQuoteResponseEntity;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MeltTest {

    private Wallet wallet;

    @Before
    public void setUp() {
        // Delete all proofs
        new ProofClient().deleteAll();

        wallet = new Wallet(PaymentMethod.MOCK);
        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(16, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void melt() {
        MeltQuoteRequestEntity meltQuoteRequestEntity = new MeltQuoteRequestEntity();
        meltQuoteRequestEntity.setPaymentMethod(PaymentMethod.MOCK.name().toLowerCase());
        meltQuoteRequestEntity.setUnit("sat");
        meltQuoteRequestEntity.setRequest(UUID.randomUUID().toString());
        meltQuoteRequestEntity.setCorrelationId(UUID.randomUUID());

        MeltQuoteRequestClient client = new MeltQuoteRequestClient();
        client.createEntity(meltQuoteRequestEntity);

        MeltQuoteResponseEntity meltQuoteResponseEntity = wallet.quoteMelt(meltQuoteRequestEntity.getRequest(), "sat");

        ProofClient proofClient = new ProofClient();

        List<ProofEntity> proofs0 = proofClient.getAllProofs();
        Integer totalAmount0 = proofs0.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        wallet.meltTokens(meltQuoteResponseEntity);

        List<ProofEntity> proofs1 = proofClient.getAllProofs();
        Integer totalAmount1 = proofs1.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        assertEquals(5, totalAmount0 - totalAmount1);

        // Melt more tokens
        meltQuoteRequestEntity = new MeltQuoteRequestEntity();
        meltQuoteRequestEntity.setPaymentMethod(PaymentMethod.MOCK.name().toLowerCase());
        meltQuoteRequestEntity.setUnit("sat");
        meltQuoteRequestEntity.setRequest(UUID.randomUUID().toString());
        meltQuoteRequestEntity.setCorrelationId(UUID.randomUUID());

        client.createEntity(meltQuoteRequestEntity);

        meltQuoteResponseEntity = wallet.quoteMelt(meltQuoteRequestEntity.getRequest(), "sat");

        proofs0 = proofClient.getAllProofs();
        totalAmount0 = proofs0.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        wallet.meltTokens(meltQuoteResponseEntity);

        proofs1 = proofClient.getAllProofs();
        totalAmount1 = proofs1.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);

        assertEquals(5, totalAmount0 - totalAmount1);
    }
}
