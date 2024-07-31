package cashu.wallet.test.client.service;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.client.MeltQuoteRequestClient;
import cashu.wallet.db.client.MeltQuoteResponseClient;
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

public class WalletTest {

    private Wallet wallet;

    @Before
    public void setUp() {
        // Delete all proofs
        new ProofClient().deleteAll();

        // Mint tokens
        wallet = new Wallet(PaymentMethod.MOCK);
        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(16, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void melt() {
        // Melt tokens
        MeltQuoteRequestEntity meltQuoteRequestEntity = new MeltQuoteRequestEntity();
        meltQuoteRequestEntity.setCorrelationId(UUID.randomUUID());
        meltQuoteRequestEntity.setUnit("sat");
        meltQuoteRequestEntity.setRequest(UUID.randomUUID().toString());
        meltQuoteRequestEntity.setPaymentMethod(PaymentMethod.MOCK.name().toLowerCase());
        new MeltQuoteRequestClient().createEntity(meltQuoteRequestEntity);

        MeltQuoteResponseEntity meltQuoteResponseEntity = new MeltQuoteResponseEntity();
        meltQuoteResponseEntity.setAmount(5);
        meltQuoteResponseEntity.setFeeReserve(0);
        meltQuoteResponseEntity.setQuote(UUID.randomUUID().toString());
        meltQuoteResponseEntity.setCorrelationId(meltQuoteRequestEntity.getCorrelationId());
        new MeltQuoteResponseClient().createEntity(meltQuoteResponseEntity);

        wallet.meltTokens(meltQuoteResponseEntity);

        List<ProofEntity> proofEntityList = new ProofClient().getAllProofs();
        assertEquals(11, proofEntityList.size());
        Integer total = proofEntityList.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);
        assertEquals(11, total.intValue());
    }

/*
    @Test
    public void melt() {
        // Melt tokens
        MeltQuoteRequestEntity meltQuoteRequestEntity = new MeltQuoteRequestEntity();
        meltQuoteRequestEntity.setCorrelationId(UUID.randomUUID());
        meltQuoteRequestEntity.setUnit("sat");
        meltQuoteRequestEntity.setRequest(UUID.randomUUID().toString());
        meltQuoteRequestEntity.setPaymentMethod(PaymentMethod.MOCK.name().toLowerCase());
        new MeltQuoteRequestClient().createEntity(meltQuoteRequestEntity);

        MeltQuoteResponseEntity meltQuoteResponseEntity = new MeltQuoteResponseEntity();
        meltQuoteResponseEntity.setAmount(16);
        meltQuoteResponseEntity.setFeeReserve(0);
        meltQuoteResponseEntity.setQuote(UUID.randomUUID().toString());
        meltQuoteResponseEntity.setCorrelationId(meltQuoteRequestEntity.getCorrelationId());
        new MeltQuoteResponseClient().createEntity(meltQuoteResponseEntity);

        wallet.meltTokens(meltQuoteResponseEntity);

        List<ProofEntity> proofEntityList = new ProofClient().getAllProofs();
        assertEquals(0, proofEntityList.size());
        Integer total = proofEntityList.stream().map(ProofEntity::getAmount).reduce(0, Integer::sum);
        assertEquals(0, total.intValue());
    }
*/
}
