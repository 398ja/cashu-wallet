package cashu.wallet.client;

import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

@AllArgsConstructor
@Log
public class Demo {

    public static void main(String[] args) {
/*
        PublicKey publicKey = getMintPublicKey();
        log.log(Level.INFO, "Mint PublicKey: {0}", publicKey.toString());

        Wallet wallet = new Wallet(PaymentMethod.MOCK);

        MintQuoteResponseEntity mintQuoteResponseEntity = wallet.quoteMint(1000, "sat");
        wallet.mintTokens(mintQuoteResponseEntity);

        MeltQuoteResponseEntity meltQuoteResponseEntity = wallet.quoteMelt("0x1234567890", "sat");
        wallet.meltTokens(meltQuoteResponseEntity);

        ProofClient proofClient = new ProofClient();
        ProofEntity proofEntity = proofClient.getByAmountAndKeysetId(512, "00c4a3dade22f81b"); // TODO - Ugly. Retrieve the keyset programmatically

        Proof proof = new Proof();
        proof.setAmount(proofEntity.getAmount());
        proof.setSecret(Secret.fromString(proofEntity.getSecret()));
        proof.setUnblindedSignature(Signature.fromString(proofEntity.getSignature()));
        proof.setKeySetId("00c4a3dade22f81b");

        BlindedMessage blindedMessage1 = new BlindedMessage();
        blindedMessage1.setAmount(256);
        blindedMessage1.setKeySetId("00c4a3dade22f81b");
        blindedMessage1.setBlindedMessage(createBlindMessage());

        BlindedMessage blindedMessage2 = new BlindedMessage();
        blindedMessage2.setAmount(256);
        blindedMessage2.setKeySetId("00c4a3dade22f81b");
        blindedMessage2.setBlindedMessage(createBlindMessage());

        SwapTokens swapTokens = new SwapTokens(PaymentMethod.MOCK, publicKey, "sat");
        PostSwapRequest postSwapRequest = new PostSwapRequest();
        postSwapRequest.setProofs(List.of(proof));
        postSwapRequest.setBlindedMessages(List.of(blindedMessage1, blindedMessage2));

        swapTokens.swap(postSwapRequest);
*/
    }
}
