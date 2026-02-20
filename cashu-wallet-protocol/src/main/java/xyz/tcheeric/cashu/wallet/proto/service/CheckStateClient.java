package xyz.tcheeric.cashu.wallet.proto.service;

import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateRequest;
import xyz.tcheeric.cashu.entities.rest.nut07.PostCheckStateResponse;

/**
 * Client for invoking the mint's NUT-07 /checkstate endpoint.
 */
public interface CheckStateClient {

    /**
     * Calls the mint's /checkstate endpoint with the provided request.
     *
     * @param mintUrl base URL of the mint (without the path)
     * @param request request body containing hashed secrets
     * @return the check state response from the mint
     */
    PostCheckStateResponse checkState(String mintUrl, PostCheckStateRequest request);
}
