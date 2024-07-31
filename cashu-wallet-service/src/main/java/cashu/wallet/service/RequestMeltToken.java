package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMeltRequest;
import cashu.common.model.rest.PostMeltResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(5)
@Service
public class RequestMeltToken extends AbstractRequestBase<PostMeltResponse, PostMeltRequest> {

    public RequestMeltToken(@NonNull PaymentMethod paymentMethod, @NonNull PostMeltRequest postMeltRequest) {
        super("/melt/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltRequest, PostMeltResponse.class);
    }
}
