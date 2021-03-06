/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.gcm.server;

public class ServerFailedException extends Exception {
  public ServerFailedException(String message) {
    super(message);
  }

  public ServerFailedException(Exception e) {
    super(e);
  }
}
