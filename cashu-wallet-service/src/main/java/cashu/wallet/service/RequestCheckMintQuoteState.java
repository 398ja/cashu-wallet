package cashu.wallet.service;

import cashu.common.annotation.Nut;
import cashu.common.model.rest.PostMintQuoteResponse;
import lombok.NonNull;
import org.springframework.stereotype.Service;

@Nut(4)
@Service
public class RequestCheckMintQuoteState extends AbstractRequestBase<PostMintQuoteResponse, Void> {

    public RequestCheckMintQuoteState(@NonNull String quoteId, @NonNull String paymentMethod) {
        super("/mint/quote/" + paymentMethod + "/" + quoteId, PostMintQuoteResponse.class);
    }

}
