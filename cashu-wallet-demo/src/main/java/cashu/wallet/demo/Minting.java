package cashu.wallet.demo;

import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

@Log
@AllArgsConstructor
public class Minting {

    private final Wallet wallet;

    public Minting () {
        this.wallet = new Wallet(PaymentMethod.MOCK);
    }

    public static void main(String[] args) {
        Minting minting = new Minting();
        minting.mint();
    }

    private void mint() {
        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(100, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);
    }
}
