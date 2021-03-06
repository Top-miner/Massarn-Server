/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.controllers;

import java.util.List;


public class StaleDevicesException extends Exception {
  private final List<Long> staleDevices;

  public StaleDevicesException(List<Long> staleDevices) {
    this.staleDevices = staleDevices;
  }

  public List<Long> getStaleDevices() {
    return staleDevices;
  }
}
