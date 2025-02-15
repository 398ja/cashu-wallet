package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.model.rest.PostCheckStateRequest;
import xyz.tcheeric.cashu.common.model.rest.PostCheckStateResponse;

public class RequestCheckState extends AbstractRequestBase<PostCheckStateResponse, PostCheckStateRequest> {

    public RequestCheckState(@NonNull String baseUrl, @NonNull PostCheckStateRequest postCheckStateRequest) {
        super(baseUrl, "/checkstate", HTTP_METHOD_POST, postCheckStateRequest, PostCheckStateResponse.class);
    }
}
