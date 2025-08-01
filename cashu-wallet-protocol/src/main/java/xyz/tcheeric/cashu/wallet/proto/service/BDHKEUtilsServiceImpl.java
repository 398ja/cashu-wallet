package xyz.tcheeric.cashu.wallet.proto.service;

import xyz.tcheeric.cashu.crypto.BDHKEUtils;

public class BDHKEUtilsServiceImpl implements BDHKEUtilsService {
    @Override
    public byte[] unblindSignature(byte[] blindedSignature, byte[] r, byte[] publicKey) {
        return BDHKEUtils.unblindSignature(blindedSignature, r, publicKey);
    }
}
