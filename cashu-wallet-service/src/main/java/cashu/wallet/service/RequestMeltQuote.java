package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMeltQuoteRequest;
import cashu.common.model.rest.PostMeltQuoteResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(5)
@Service
public class RequestMeltQuote extends AbstractRequestBase<PostMeltQuoteResponse, PostMeltQuoteRequest> {

    public RequestMeltQuote(@NonNull PaymentMethod paymentMethod, @NonNull PostMeltQuoteRequest postMeltQuoteRequest) {
        super("/melt/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltQuoteRequest, PostMeltQuoteResponse.class);
    }
}
