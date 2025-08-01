package xyz.tcheeric.cashu.wallet.proto.service;

public interface BDHKEUtilsService {
    byte[] unblindSignature(byte[] blindedSignature, byte[] r, byte[] publicKey);
}
