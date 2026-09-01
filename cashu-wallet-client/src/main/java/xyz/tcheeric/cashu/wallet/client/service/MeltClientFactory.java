package xyz.tcheeric.cashu.wallet.client.service;

import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.nut18.PaymentMethod;
import xyz.tcheeric.cashu.entities.rest.nut05.PostMeltRequest;
import xyz.tcheeric.cashu.wallet.client.impl.RequestMeltToken;

/**
 * Factory for constructing {@link RequestMeltToken} instances. Enables tests to provide
 * deterministic stubs while production code uses the real REST client.
 */
@FunctionalInterface
public interface MeltClientFactory {

    /**
     * Creates a melt client for the given mint URL and request payload.
     *
     * @param mintUrl Mint base URL
     * @param paymentMethod Payment method segment of the melt path
     * @param request Melt request payload, including NUT-08 blank outputs
     * @return configured RequestMeltToken instance
     */
    <T extends Secret> RequestMeltToken<T> create(
            String mintUrl,
            PaymentMethod paymentMethod,
            PostMeltRequest<T> request
    );
}
