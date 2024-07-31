package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMintRequest;
import cashu.common.model.rest.PostMintResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(4)
@Service
public class RequestMintToken extends AbstractRequestBase<PostMintResponse, PostMintRequest> {

    public RequestMintToken(@NonNull PaymentMethod paymentMethod, @NonNull PostMintRequest postMintRequest) {
        super("/mint/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintRequest, PostMintResponse.class);
    }
}
