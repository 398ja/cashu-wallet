package cashu.wallet.client.operation;

import cashu.common.model.BlindSignature;
import cashu.common.model.PaymentMethod;
import cashu.common.model.Proof;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.model.rest.PostMintQuoteRequest;
import cashu.common.model.rest.PostMintQuoteResponse;
import cashu.common.model.rest.PostMintRequest;
import cashu.common.model.rest.PostMintResponse;
import cashu.gateway.Gateway;
import cashu.util.Utils;
import cashu.wallet.client.util.WalletUtil;
import cashu.wallet.db.client.MintQuoteResponseClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.model.MintQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import cashu.wallet.proto.tasks.UnblindSignatureTask;
import cashu.wallet.service.RequestMintQuote;
import cashu.wallet.service.RequestMintToken;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static cashu.wallet.client.util.WalletUtil.getUnit;

@Log
@Getter
public class MintTokens extends BaseOperation {

    public MintTokens(@NonNull PaymentMethod paymentMethod) {
        super(paymentMethod);
    }

    public MintQuoteResponseEntity quote(@NonNull MintQuoteRequestEntity mintQuoteRequestEntity) {
        log.log(Level.FINE, "quote({0})", mintQuoteRequestEntity);

        PostMintQuoteRequest postMintQuoteRequest = new PostMintQuoteRequest();
        postMintQuoteRequest.setUnit(mintQuoteRequestEntity.getUnit());
        postMintQuoteRequest.setAmount(mintQuoteRequestEntity.getAmount());

        RequestMintQuote requestMintQuote = new RequestMintQuote(getPaymentMethod(), postMintQuoteRequest);

        MintQuoteResponseEntity mintQuoteResponseEntity = new MintQuoteResponseEntity();

        CompletableFuture<PostMintQuoteResponse> completableFuture = CompletableFuture.supplyAsync(() -> requestMintQuote.execute());

        // TODO - Make timeout configurable
        PostMintQuoteResponse response = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes

        if (response == null) {
            throw new IllegalStateException("Quote not received");
        }

        log.log(Level.INFO, "quote response: {0}", response);

        mintQuoteResponseEntity.setCorrelationId(mintQuoteRequestEntity.getCorrelationId());
        mintQuoteResponseEntity.setQuote(response.getQuoteId());
        mintQuoteResponseEntity.setRequest(response.getRequest());

        // Save the entity
        MintQuoteResponseClient client = new MintQuoteResponseClient();
        client.createEntity(mintQuoteResponseEntity);

        return mintQuoteResponseEntity;
    }

    public void mint(@NonNull PostMintRequest postMintRequest) {
        log.log(Level.INFO, "mint({0})", postMintRequest);

        // TODO - Check if the quote is paid. Should be done by the mint or wallet or both?
        // Make sure the quote is paid
        Gateway gateway = createGateway("mint");
        if (!gateway.checkPaymentStatus(postMintRequest.getQuoteId())) {
            throw new RuntimeException("Payment not received yet");
        }

        // Mint the tokens
        RequestMintToken requestMintToken = new RequestMintToken(getPaymentMethod(), postMintRequest);
        CompletableFuture<PostMintResponse> completableFuture = CompletableFuture.supplyAsync(requestMintToken::execute);
        PostMintResponse mintResponse = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes
        if (mintResponse == null) {
            throw new IllegalStateException("Mint mintResponse not received");
        }

        log.log(Level.INFO, "mint mintResponse: {0}", mintResponse);

        // Sanity checks
        List<BlindSignature> signatures = mintResponse.getBlindSignatures();
        assert signatures.size() == postMintRequest.getSecrets().size();
        assert signatures.size() == postMintRequest.getBlindingFactors().size();

        // Un-blind the signatures
        for (int i = 0; i < signatures.size(); i++) {

            BlindSignature C_ = signatures.get(i);
            Secret secret = postMintRequest.getSecret(i);
            BigInteger r = Utils.bigIntFromBytes(postMintRequest.getBlindingFactor(i));

            // Un-blind the blind signature (C_)
            String keySetId = C_.getKeySetId();
            int amount = C_.getAmount();
            String unit = getUnit(keySetId);
            PublicKey K = WalletUtil.getPublicKey(keySetId, unit, amount);
            UnblindSignatureTask unblindSignatureTask = new UnblindSignatureTask(C_, r, K, secret);
            Proof proof = unblindSignatureTask.execute();

            // Create the proof entity
            ProofEntity proofEntity = ProofEntity.fromProof(proof);

            // Store the proof entity
            log.log(Level.FINE, "*** Storing proof entity: {0}", proofEntity.toString());
            ProofClient proofClient = new ProofClient();
            proofClient.createEntity(proofEntity);
        }
    }
}
