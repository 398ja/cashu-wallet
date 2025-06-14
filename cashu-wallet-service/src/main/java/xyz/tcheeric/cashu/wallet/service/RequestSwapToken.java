package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.rest.PostSwapRequest;
import xyz.tcheeric.cashu.entities.rest.PostSwapResponse;

public class RequestSwapToken<T extends Secret> extends AbstractRequestBase<PostSwapResponse, PostSwapRequest> {

    public RequestSwapToken(@NonNull String baseUrl, @NonNull PostSwapRequest<T> postSwapRequest) {
        super(baseUrl, "/swap", HTTP_METHOD_POST, postSwapRequest, PostSwapResponse.class);
    }
}
