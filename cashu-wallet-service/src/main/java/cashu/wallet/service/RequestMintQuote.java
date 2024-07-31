package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.PaymentMethod;
import cashu.common.model.rest.PostMintQuoteRequest;
import cashu.common.model.rest.PostMintQuoteResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(4)
@Service
public class RequestMintQuote extends AbstractRequestBase<PostMintQuoteResponse, PostMintQuoteRequest> {

    public RequestMintQuote(@NonNull PaymentMethod paymentMethod, @NonNull PostMintQuoteRequest postMintQuoteRequest) {
        super("/mint/quote/" + paymentMethod.name().toLowerCase(), HTTP_METHOD_POST, postMintQuoteRequest,PostMintQuoteResponse.class);
    }
}
