package cashu.wallet.service;

import cashu.common.model.rest.PostCheckStateRequest;
import cashu.common.model.rest.PostCheckStateResponse;
import lombok.NonNull;

public class RequestCheckState extends AbstractRequestBase<PostCheckStateResponse, PostCheckStateRequest> {

    public RequestCheckState(@NonNull PostCheckStateRequest postCheckStateRequest) {
        super("/checkstate", HTTP_METHOD_POST, postCheckStateRequest, PostCheckStateResponse.class);
    }
}
