/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.storage;

public class ContestedOptimisticLockException extends RuntimeException {

  public ContestedOptimisticLockException() {
    super(null, null, true, false);
  }
}
