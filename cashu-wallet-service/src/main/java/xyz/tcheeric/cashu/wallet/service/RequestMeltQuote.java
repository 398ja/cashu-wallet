package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.PaymentMethod;
import xyz.tcheeric.cashu.common.model.rest.PostMeltQuoteRequest;
import xyz.tcheeric.cashu.common.model.rest.PostMeltQuoteResponse;

@Nut(5)
@Service
public class RequestMeltQuote extends AbstractRequestBase<PostMeltQuoteResponse, PostMeltQuoteRequest> {

    public RequestMeltQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMeltQuoteRequest postMeltQuoteRequest) {
        super(baseUrl, "/melt/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltQuoteRequest, PostMeltQuoteResponse.class);
    }
}
