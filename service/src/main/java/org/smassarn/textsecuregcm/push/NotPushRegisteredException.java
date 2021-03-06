/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.push;

public class NotPushRegisteredException extends Exception {
  public NotPushRegisteredException() {
    super();
  }

  public NotPushRegisteredException(String s) {
    super(s);
  }

  public NotPushRegisteredException(Exception e) {
    super(e);
  }
}
