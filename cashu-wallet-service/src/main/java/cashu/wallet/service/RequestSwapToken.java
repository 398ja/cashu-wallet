package cashu.wallet.service;

import cashu.common.model.rest.PostSwapRequest;
import cashu.common.model.rest.PostSwapResponse;
import lombok.NonNull;

public class RequestSwapToken extends AbstractRequestBase<PostSwapResponse, PostSwapRequest> {

    public RequestSwapToken(@NonNull PostSwapRequest postSwapRequest) {
        super("/swap", HTTP_METHOD_POST, postSwapRequest, PostSwapResponse.class);
    }
}
