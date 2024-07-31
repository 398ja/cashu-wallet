package cashu.wallet.demo;


import cashu.common.model.PaymentMethod;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.client.util.WalletUtil;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import lombok.AllArgsConstructor;

import java.util.Map;

@AllArgsConstructor
public class Swapping {

    private Wallet wallet;

    public Swapping() {
        this.wallet = new Wallet(PaymentMethod.MOCK);
    }

    public static void main(String[] args) {
        Swapping swapping = new Swapping();
        swapping.swap();
    }

    private void swap() {

        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(10, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);

        System.out.println("=== Initial tokens");
        WalletUtil.printTokens();

        ProofEntity proofEntity = new ProofClient().getByAmountAndKeysetId(8, WalletUtil.getKeysetId("sat"));
        wallet.swapTokens(proofEntity, Map.of(4, 2), "sat");

        System.out.println("=== Final tokens");
        WalletUtil.printTokens();
    }
}
