package xyz.tcheeric.cashu.wallet.client.service;

import xyz.tcheeric.cashu.entities.rest.PostRestoreRequest;
import xyz.tcheeric.cashu.wallet.client.impl.RequestRestore;

/**
 * Factory for constructing {@link RequestRestore} instances. Enables tests to provide
 * deterministic stubs while production code uses the real REST client.
 */
@FunctionalInterface
public interface RestoreClientFactory {

    /**
     * Creates a restore client for the given mint URL and request payload.
     *
     * @param mintUrl Mint base URL
     * @param request Restore request payload
     * @return configured RequestRestore instance
     */
    RequestRestore create(String mintUrl, PostRestoreRequest request);
}
