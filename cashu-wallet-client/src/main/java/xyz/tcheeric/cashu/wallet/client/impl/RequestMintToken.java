package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintRequest;
import xyz.tcheeric.cashu.entities.rest.nut04.PostMintResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(4)
public class RequestMintToken<T extends Secret> extends AbstractRequestBase<PostMintResponse, PostMintRequest<T>> {

    public RequestMintToken(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMintRequest<T> postMintRequest) {
        super(baseUrl, "/mint/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintRequest, PostMintResponse.class);
    }
}
