package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.PaymentMethod;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMintRequest;
import xyz.tcheeric.cashu.entities.rest.PostMintResponse;

@Nut(4)
@Service
public class RequestMintToken<T extends Secret> extends AbstractRequestBase<PostMintResponse, PostMintRequest<T>> {

    public RequestMintToken(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMintRequest<T> postMintRequest) {
        super(baseUrl, "/mint/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintRequest, PostMintResponse.class);
    }
}
