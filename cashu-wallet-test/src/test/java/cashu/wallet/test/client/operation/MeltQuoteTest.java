package cashu.wallet.test.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.operation.MeltTokens;
import cashu.wallet.client.operation.MintTokens;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.client.MeltQuoteRequestClient;
import cashu.wallet.db.model.MeltQuoteRequestEntity;
import cashu.wallet.db.model.MeltQuoteResponseEntity;
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

public class MeltQuoteTest {
    @Captor
    ArgumentCaptor<MeltQuoteRequestEntity> captor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        captor = ArgumentCaptor.forClass(MeltQuoteRequestEntity.class);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void quote() {
        MeltQuoteRequestClient client = mock(MeltQuoteRequestClient.class);
        MintTokens mintTokens = mock(MintTokens.class);
        MeltTokens meltTokens = mock(MeltTokens.class);
        Wallet wallet = spy(new Wallet(PaymentMethod.MOCK, null, client, mintTokens, meltTokens));

        when(wallet.quoteMelt("request", "sat")).thenReturn(new MeltQuoteResponseEntity());

        wallet.quoteMelt("request", "sat");

        verify(client).createEntity(captor.capture());
    }
}
