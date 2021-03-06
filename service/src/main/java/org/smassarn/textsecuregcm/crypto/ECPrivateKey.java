/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.crypto;

public interface ECPrivateKey {
  public byte[] serialize();
  public int getType();
}

