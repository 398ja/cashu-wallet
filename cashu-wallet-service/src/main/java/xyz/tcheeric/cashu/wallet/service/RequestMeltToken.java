package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.PaymentMethod;
import xyz.tcheeric.cashu.common.model.Secret;
import xyz.tcheeric.cashu.common.model.rest.PostMeltRequest;
import xyz.tcheeric.cashu.common.model.rest.PostMeltResponse;

@Nut(5)
@Service
public class RequestMeltToken<T extends Secret> extends AbstractRequestBase<PostMeltResponse, PostMeltRequest<T>> {

    public RequestMeltToken(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMeltRequest<T> postMeltRequest) {
        super(baseUrl, "/melt/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltRequest, PostMeltResponse.class);
    }
}
