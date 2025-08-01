package xyz.tcheeric.cashu.wallet.proto.nut;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.wallet.proto.tasks.UnblindSignatureTask;

import java.math.BigInteger;

@Log
public class NUT04 {

    public static <T extends Secret> Proof<T> unblindingSignature(@NonNull BlindSignature blindSignature, @NonNull BigInteger r, @NonNull PublicKey K, @NonNull T secret) {
        return new UnblindSignatureTask<>(blindSignature, r, K, secret).execute();
    }
}
