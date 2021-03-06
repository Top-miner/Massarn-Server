/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.crypto;

public interface ECPublicKey extends Comparable<ECPublicKey> {

  public static final int KEY_SIZE = 33;

  public byte[] serialize();

  public int getType();
}
