/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.crypto;

public class ECKeyPair {

  private final ECPublicKey  publicKey;
  private final ECPrivateKey privateKey;

  ECKeyPair(ECPublicKey publicKey, ECPrivateKey privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  public ECPrivateKey getPrivateKey() {
    return privateKey;
  }
}
