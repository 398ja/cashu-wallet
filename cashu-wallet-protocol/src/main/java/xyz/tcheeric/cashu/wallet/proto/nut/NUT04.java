package xyz.tcheeric.cashu.wallet.proto.nut;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.wallet.proto.tasks.UnblindSignatureTask;

import java.math.BigInteger;

@Slf4j
public class NUT04 {

    public static <T extends Secret> Proof<T> unblindingSignature(@NonNull BlindSignature blindSignature, @NonNull BigInteger r, @NonNull PublicKey K, @NonNull T secret) {
        log.debug("nut04 unblinding_requested keyset={} amount={}",
            blindSignature.getKeySetId(), blindSignature.getAmount());
        Proof<T> proof = new UnblindSignatureTask<>(blindSignature, r, K, secret).execute();
        log.debug("nut04 unblinding_completed keyset={} amount={}", blindSignature.getKeySetId(), blindSignature.getAmount());
        return proof;
    }
}
