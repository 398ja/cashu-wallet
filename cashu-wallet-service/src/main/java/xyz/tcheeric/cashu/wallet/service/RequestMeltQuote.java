package xyz.tcheeric.cashu.wallet.service;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.PaymentMethod;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.PostMeltQuoteRequest;
import xyz.tcheeric.cashu.entities.rest.PostMeltQuoteResponse;

@Nut(5)
public class RequestMeltQuote extends AbstractRequestBase<PostMeltQuoteResponse, PostMeltQuoteRequest> {

    public RequestMeltQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMeltQuoteRequest postMeltQuoteRequest) {
        super(baseUrl, "/melt/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltQuoteRequest, PostMeltQuoteResponse.class);
    }
}
