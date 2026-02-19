package xyz.tcheeric.cashu.wallet.client.impl;

import lombok.NonNull;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteRequest;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltQuoteResponse;
import xyz.tcheeric.cashu.wallet.client.AbstractRequestBase;

@Nut(5)
public class RequestMeltQuote extends AbstractRequestBase<PostMeltQuoteResponse, PostMeltQuoteRequest> {

    public RequestMeltQuote(@NonNull String baseUrl, @NonNull PaymentMethod paymentMethod, @NonNull PostMeltQuoteRequest postMeltQuoteRequest) {
        super(baseUrl, "/melt/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMeltQuoteRequest, PostMeltQuoteResponse.class);
    }
}
