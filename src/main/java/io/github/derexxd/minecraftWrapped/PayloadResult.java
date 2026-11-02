package io.github.derexxd.minecraftWrapped;

public class PayloadResult {

    byte[] data;
    String hash;

    PayloadResult(byte[] data, String hash) {
        this.data = data;
        this.hash = hash;
    }
}

