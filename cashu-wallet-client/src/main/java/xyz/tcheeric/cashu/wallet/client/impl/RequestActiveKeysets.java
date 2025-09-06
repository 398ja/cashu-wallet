package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.GetActiveKeySetsResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(2)
public class RequestActiveKeysets extends AbstractRequestBase<GetActiveKeySetsResponse, Void> {

    public RequestActiveKeysets(@NonNull String baseUrl) {
        super(baseUrl, "/keysets", GetActiveKeySetsResponse.class);
    }
}
