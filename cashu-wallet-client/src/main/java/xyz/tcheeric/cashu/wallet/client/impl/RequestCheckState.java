package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateRequest;
import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

public class RequestCheckState extends AbstractRequestBase<PostCheckStateResponse, PostCheckStateRequest> {

    public RequestCheckState(@NonNull String baseUrl, @NonNull PostCheckStateRequest postCheckStateRequest) {
        super(baseUrl, "/checkstate", HTTP_METHOD_POST, postCheckStateRequest, PostCheckStateResponse.class);
    }
}
