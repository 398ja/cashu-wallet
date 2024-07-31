package cashu.wallet.test.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.operation.MeltTokens;
import cashu.wallet.client.operation.MintTokens;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.client.MintQuoteRequestClient;
import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MintQuoteTest {

    @Captor
    ArgumentCaptor<MintQuoteRequestEntity> captor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        captor = ArgumentCaptor.forClass(MintQuoteRequestEntity.class);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void quote() {
        MintQuoteRequestClient client = mock(MintQuoteRequestClient.class);
        MintTokens mintTokens = mock(MintTokens.class);
        MeltTokens meltTokens = mock(MeltTokens.class);
        Wallet wallet = spy(new Wallet(PaymentMethod.MOCK, client, null, mintTokens, meltTokens));

        when(wallet.quoteMint(100, "sat")).thenReturn(new MintQuoteResponseEntity());

        wallet.quoteMint(100, "sat");

        verify(client).createEntity(captor.capture());
    }
}
