package cashu.wallet.client.operation;

import cashu.common.model.CryptoElement;
import cashu.common.model.PaymentMethod;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.model.rest.PostCheckStateRequest;
import cashu.common.model.rest.PostCheckStateResponse;
import cashu.crypto.BDHKEUtils;
import cashu.wallet.service.RequestCheckState;
import lombok.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CheckStateTokens extends BaseOperation {

    public CheckStateTokens(@NonNull PaymentMethod paymentMethod) {
        super(paymentMethod);
    }

    public PostCheckStateResponse checkState(@NonNull List<Secret> secrets) {

        List<CryptoElement> hashToCurveSecrets = secrets
                .stream()
                .map(s -> BDHKEUtils.hashToCurve(s.toString()))
                .map(b -> PublicKey.fromBytes(b))
                .collect(Collectors.toList());

        PostCheckStateRequest postCheckStateRequest = new PostCheckStateRequest(hashToCurveSecrets);
        RequestCheckState requestCheckState = new RequestCheckState(postCheckStateRequest);
        CompletableFuture<PostCheckStateResponse> completableFuture = CompletableFuture.supplyAsync(() -> requestCheckState.execute());

        PostCheckStateResponse postCheckStateResponse = completableFuture.orTimeout(5, TimeUnit.SECONDS).join(); // This line blocks until the CompletableFuture completes
        if (postCheckStateResponse == null) {
            throw new IllegalStateException("Proofs' states not received");
        }

        return postCheckStateResponse;
    }
}
