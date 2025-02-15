package xyz.tcheeric.cashu.wallet.proto.nut;

import lombok.NonNull;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.model.BlindSignature;
import xyz.tcheeric.cashu.common.model.Proof;
import xyz.tcheeric.cashu.common.model.PublicKey;
import xyz.tcheeric.cashu.common.model.Secret;
import xyz.tcheeric.cashu.common.model.Signature;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;

import java.math.BigInteger;

@Log
public class NUT04 {

    public static <T extends Secret> Proof<T> unblindingSignature(@NonNull BlindSignature blindSignature, @NonNull BigInteger r, @NonNull PublicKey K, @NonNull T secret) {
        Signature C_ = blindSignature.getBlindedSignature();
        byte[] C = BDHKEUtils.unblindSignature(C_.getBytes(), Utils.bytesFromBigInteger(r), K.getBytes());
        return Proof.<T>builder().secret(secret).amount(blindSignature.getAmount()).keySetId(blindSignature.getKeySetId()).unblindedSignature(Signature.fromBytes(C)).build();
    }
}
