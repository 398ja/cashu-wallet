package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.GetActiveKeySetsResponse;

@Nut(2)
public class RequestActiveKeysets extends AbstractRequestBase<GetActiveKeySetsResponse, Void> {

    public RequestActiveKeysets(@NonNull String baseUrl) {
        super(baseUrl, "/keysets", GetActiveKeySetsResponse.class);
    }
}
