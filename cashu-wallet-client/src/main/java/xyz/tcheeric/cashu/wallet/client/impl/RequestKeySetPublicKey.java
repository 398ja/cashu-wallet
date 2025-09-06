package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.GetKeySetsResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(1)
public class RequestKeySetPublicKey extends AbstractRequestBase<GetKeySetsResponse, Void> {

    public RequestKeySetPublicKey(@NonNull String baseUrl) {
        super(baseUrl, "/keys", GetKeySetsResponse.class);
    }

    public RequestKeySetPublicKey(@NonNull String baseUrl, @NonNull String keySetId) {
        super(baseUrl, "/keys/" + keySetId, GetKeySetsResponse.class);
    }
}
