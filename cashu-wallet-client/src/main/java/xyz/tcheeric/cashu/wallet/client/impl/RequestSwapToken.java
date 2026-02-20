package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.rest.nut03.PostSwapRequest;
import xyz.tcheeric.cashu.entities.rest.nut03.PostSwapResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

public class RequestSwapToken<T extends Secret> extends AbstractRequestBase<PostSwapResponse, PostSwapRequest> {

    public RequestSwapToken(@NonNull String baseUrl, @NonNull PostSwapRequest<T> postSwapRequest) {
        super(baseUrl, "/swap", HTTP_METHOD_POST, postSwapRequest, PostSwapResponse.class);
    }
}
