package cashu.wallet.client.util;

import cashu.common.model.BlindedMessage;
import cashu.common.model.KeySet;
import cashu.common.model.Proof;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.model.rest.PostMintRequest;
import cashu.crypto.BDHKEUtils;
import cashu.util.Utils;
import cashu.wallet.db.client.MintRequestClient;
import cashu.wallet.db.client.ProofClient;
import cashu.wallet.db.model.MintQuoteRequestEntity;
import cashu.wallet.db.model.MintRequestEntity;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

public class WalletUtil {

    public static PublicKey createBlindMessage() {
        return createBlindMessage(Secret.create().toBytes());
    }

    public static PublicKey createBlindMessage(@NonNull Proof proof) {
        return createBlindMessage(proof.getSecret().toBytes());
    }

    public static PublicKey createBlindMessage(@NonNull byte[] secret) {
        return PublicKey.fromBytes(BDHKEUtils.blindMessage(secret)[0]);
    }

    public static PublicKey createBlindMessage(@NonNull byte[] secret, @NonNull byte[] r) {
        return PublicKey.fromBytes(BDHKEUtils.blindMessage(secret, r));
    }

    public static byte[] getBlindingFactor(@NonNull Secret secret) {
        return BDHKEUtils.blindMessage(secret.toBytes())[1];
    }

    public static PublicKey getPublicKey(@NonNull String keysetId, @NonNull String unit, @NonNull Integer amount) {
        MintRestClient cashuClient = new MintRestClient(unit);
        List<KeySet> keySetList = cashuClient.keys();
        for (KeySet ks : keySetList) {
            if (ks.getId().equals(keysetId)) {
                return ks.getKeys().get(amount);
            }
        }

        throw new IllegalStateException("Keyset not found");
    }

    public static String getUnit(@NonNull String keySetId) {
        MintRestClient mintRestClient = new MintRestClient();
        List<KeySet> keys = mintRestClient.keys(keySetId);
        return keys.stream().filter(key -> key.getId().equals(keySetId)).findFirst().get().getUnit();
    }

    public static String getKeysetId(@NonNull String unit) {
        MintRestClient mintRestClient = new MintRestClient(unit);
        return mintRestClient.getKeysetId();
    }

    public static void printTokens() {
        new ProofClient().getAllProofs().stream().forEach(p -> System.out.println(p.toString()));
    }

    public static PostMintRequest createPostMintRequest(@NonNull String quote, @NonNull MintQuoteRequestEntity mintQuoteRequestEntity) {

        Integer totalAmount = mintQuoteRequestEntity.getAmount();
        String unit = mintQuoteRequestEntity.getUnit();

        String keysetId = getKeysetId(unit);
        Map<Integer, Integer> change = ChangeCalculator.withAllDenominations(totalAmount, unit);
        return createPostMintRequest(quote, mintQuoteRequestEntity, change, keysetId);
    }

    private static PostMintRequest createPostMintRequest(
            @NonNull String quote,
            @NonNull MintQuoteRequestEntity mintQuoteRequestEntity,
            @NonNull Map<Integer, Integer> split,
            @NonNull String keysetId)
    {
        PostMintRequest postMintRequest = new PostMintRequest();
        int counter = 0;

        for (Map.Entry<Integer, Integer> entry : split.entrySet()) {
            int splitAmount = entry.getKey();
            int splitCount = entry.getValue();
            for (int i = 0; i < splitCount; i++) {

                Secret secret = Secret.create();
                byte[][] blindedMessage = BDHKEUtils.blindMessage(secret.toBytes());
                byte[] r = blindedMessage[1];
                PublicKey B_ = PublicKey.fromBytes(blindedMessage[0]);
                BlindedMessage bmsg = new BlindedMessage(splitAmount, keysetId, B_);

                postMintRequest.addSecret(secret, counter);
                postMintRequest.addBlindingFactor(r, counter);
                postMintRequest.addBlindMessage(bmsg, counter);

                MintRequestEntity mintRequestEntity = new MintRequestEntity();
                mintRequestEntity.setBlindingFactor(Utils.bytesToHexString(r));
                mintRequestEntity.setBlindMessage(B_.toString());
                mintRequestEntity.setSecret(secret.toString());
                mintRequestEntity.setCorrelationId(mintQuoteRequestEntity.getCorrelationId());
                mintRequestEntity.setAmount(splitAmount);
                mintRequestEntity.setKeysetId(keysetId);

                //  Persists the MintRequestEntity...
                MintRequestClient client = new MintRequestClient();
                client.createEntity(mintRequestEntity);

                counter++;
            }
        }

        postMintRequest.setQuoteId(quote);

        return postMintRequest;
    }

}
