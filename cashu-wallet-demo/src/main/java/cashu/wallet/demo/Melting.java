package cashu.wallet.demo;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.model.MeltQuoteResponseEntity;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

import java.util.UUID;

@Log
@AllArgsConstructor
public class Melting {

    private Wallet wallet;

    public Melting () {
        this.wallet = new Wallet(PaymentMethod.MOCK);
    }

    public static void main(String[] args) {
        Melting melting = new Melting();
        melting.melt();
    }

    private void melt() {
        MeltQuoteResponseEntity meltQuoteResponseEntity = wallet.quoteMelt(UUID.randomUUID().toString(), "sat");
        wallet.meltTokens(meltQuoteResponseEntity);
    }
}
