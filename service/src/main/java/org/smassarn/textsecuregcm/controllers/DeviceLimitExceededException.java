/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.controllers;


public class DeviceLimitExceededException extends Exception {

  private final int currentDevices;
  private final int maxDevices;

  public DeviceLimitExceededException(int currentDevices, int maxDevices) {
    this.currentDevices = currentDevices;
    this.maxDevices     = maxDevices;
  }

  public int getCurrentDevices() {
    return currentDevices;
  }

  public int getMaxDevices() {
    return maxDevices;
  }
}
