package cashu.wallet.client.operation;

import cashu.common.model.PaymentMethod;
import cashu.common.model.Proof;
import cashu.common.model.rest.PostMeltQuoteRequest;
import cashu.common.model.rest.PostMeltQuoteResponse;
import cashu.common.model.rest.PostMeltRequest;
import cashu.common.model.rest.PostMeltResponse;
import cashu.wallet.db.client.MeltQuoteResponseClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MeltQuoteRequestEntity;
import cashu.wallet.db.model.MeltQuoteResponseEntity;
import cashu.wallet.db.model.ProofEntity;
import cashu.wallet.service.RequestMeltQuote;
import cashu.wallet.service.RequestMeltToken;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Log
public class MeltTokens extends BaseOperation {


    public MeltTokens(@NonNull PaymentMethod paymentMethod) {
        super(paymentMethod);
    }

    public MeltQuoteResponseEntity quote(@NonNull MeltQuoteRequestEntity meltQuoteRequestEntity) {
        log.log(Level.FINE, "quote({0})", meltQuoteRequestEntity);

        PostMeltQuoteRequest postMeltQuoteRequest = createPostMeltQuoteRequest();
        postMeltQuoteRequest.setRequestId(meltQuoteRequestEntity.getRequest());

        RequestMeltQuote requestMeltQuote = new RequestMeltQuote(getPaymentMethod(), postMeltQuoteRequest);
        CompletableFuture<PostMeltQuoteResponse> completableFuture = CompletableFuture.supplyAsync(() -> requestMeltQuote.execute());

        PostMeltQuoteResponse postMeltQuoteResponse = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes
        if (postMeltQuoteResponse == null) {
            throw new IllegalStateException("Quote not received");
        }

        log.log(Level.INFO, "quote postMeltQuoteResponse: {0}", postMeltQuoteResponse);

        MeltQuoteResponseEntity meltQuoteResponseEntity = new MeltQuoteResponseEntity();
        meltQuoteResponseEntity.setCorrelationId(meltQuoteRequestEntity.getCorrelationId());
        meltQuoteResponseEntity.setQuote(postMeltQuoteResponse.getQuoteId());
        meltQuoteResponseEntity.setFeeReserve(postMeltQuoteResponse.getFeeReserve());
        meltQuoteResponseEntity.setAmount(postMeltQuoteResponse.getAmount());

        // Save the entity
        MeltQuoteResponseClient client = new MeltQuoteResponseClient();
        client.createEntity(meltQuoteResponseEntity);

        return meltQuoteResponseEntity;
    }

    public void melt(@NonNull PostMeltRequest postMeltRequest) {
        log.log(Level.FINE, "melt({0})", postMeltRequest);

        // Get the response from the quote
        MeltQuoteResponseClient meltQuoteResponseClient = new MeltQuoteResponseClient();
        MeltQuoteResponseEntity meltQuoteResponse = meltQuoteResponseClient.getByQuote(postMeltRequest.getQuoteId()); // Get the mintQuoteResponse from the quote

        // TODO - Check if the quote is paid. Should be done by the mint or wallet or both?

        // Melt the tokens
        RequestMeltToken requestMeltToken = new RequestMeltToken(getPaymentMethod(), postMeltRequest);
        CompletableFuture<PostMeltResponse> completableFuture = CompletableFuture.supplyAsync(requestMeltToken::execute);

        // TODO - Make timeout configurable
        PostMeltResponse postMeltResponse = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes
        if (postMeltResponse == null) {
            throw new IllegalStateException("Melting error occurred. PostMeltResponse not received");
        }

        log.log(Level.INFO, "melt response: {0}", requestMeltToken);

        // Delete the melted proofs
        if (postMeltResponse.isPaid()) {
            ProofClient proofClient = new ProofClient();
            List<Proof> meltedProofs = postMeltRequest.getProofs();
            meltedProofs.forEach(p -> {
                String signature = p.getUnblindedSignature().toString();
                ProofEntity proofEntity = proofClient.getBySignature(signature);
                if (proofEntity == null) {
                    throw new IllegalStateException("No proofEntity not found with signature " + signature);
                }
                log.log(Level.INFO, "Deleting proofEntity {0}", proofEntity);
                proofClient.deleteEntity(proofEntity.getId());
            });

            return;
        }

        throw new IllegalStateException("Payment not received yet");
    }
}
