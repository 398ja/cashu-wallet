package cashu.wallet.client.operation;

import cashu.common.model.BlindedMessage;
import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMintRequest;
import cashu.common.model.rest.PostSwapRequest;
import cashu.common.model.rest.PostSwapResponse;
import cashu.wallet.client.service.Wallet;
import cashu.wallet.client.util.WalletUtil;
import cashu.wallet.db.client.MintQuoteRequestClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import cashu.wallet.service.RequestSwapToken;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Log
public class SwapTokens extends BaseOperation {

    private final String unit;

    public SwapTokens(@NonNull PaymentMethod paymentMethod, @NonNull String unit) {
        super(paymentMethod);
        this.unit = unit;
    }

    public void swap(@NonNull PostSwapRequest postSwapRequest, @NonNull Wallet.ProofSecretRecordList proofSecretRecordList) {
        log.log(Level.INFO, "swap({0}, {1})", new Object[]{postSwapRequest, proofSecretRecordList});

        // Swap the tokens
        RequestSwapToken requestSwapToken = new RequestSwapToken(postSwapRequest);
        CompletableFuture<PostSwapResponse> completableFuture = CompletableFuture.supplyAsync(requestSwapToken::execute);

        // TODO - Make the timeout configurable
        PostSwapResponse postSwapResponse = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes
        if (postSwapResponse == null) {
            throw new IllegalStateException("Post swap response not received");
        }
        log.log(Level.INFO, "swap response: {0}", postSwapResponse);

        // Delete the spent proofs
        ProofClient proofClient = new ProofClient();
        postSwapRequest.getProofs().forEach(p -> {
            ProofEntity proofEntity = proofClient.getByAmountAndKeysetId(p.getAmount(), p.getKeySetId());
            log.log(Level.INFO, "Deleting proof: {0}", proofEntity.getId());
            proofClient.deleteEntity(proofEntity.getId());
        });

        // Mint the new proofs
        PaymentMethod paymentMethod = getPaymentMethod();
        List<BlindedMessage> blindedMessages = postSwapRequest.getBlindedMessages();

        blindedMessages.forEach(bm -> {
            // Create the mint quote requests
            MintQuoteRequestEntity mintQuoteRequestEntity = new MintQuoteRequestEntity();
            mintQuoteRequestEntity.setUnit(unit);
            mintQuoteRequestEntity.setPaymentMethod(paymentMethod.name().toLowerCase());
            mintQuoteRequestEntity.setCorrelationId(UUID.randomUUID());
            mintQuoteRequestEntity.setAmount(bm.getAmount());
            new MintQuoteRequestClient().createEntity(mintQuoteRequestEntity);

            // Request a quotes
            MintTokens mintTokens = new MintTokens(paymentMethod);
            MintQuoteResponseEntity mintQuoteResponseEntity = mintTokens.quote(mintQuoteRequestEntity);

            // Mint
            PostMintRequest postMintRequest = WalletUtil.createPostMintRequest(mintQuoteResponseEntity.getQuote(), mintQuoteRequestEntity);
            mintTokens.mint(postMintRequest);
        });

    }
}
