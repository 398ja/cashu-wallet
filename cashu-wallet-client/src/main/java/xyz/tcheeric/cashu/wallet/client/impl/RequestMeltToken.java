package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.PaymentMethod;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMeltRequest;
import xyz.tcheeric.cashu.entities.rest.PostMeltResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(5)
public class RequestMeltToken<T extends Secret> extends AbstractRequestBase<PostMeltResponse, PostMeltRequest<T>> {

    public RequestMeltToken(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMeltRequest<T> postMeltRequest) {
        super(baseUrl, "/melt/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltRequest, PostMeltResponse.class);
    }
}
