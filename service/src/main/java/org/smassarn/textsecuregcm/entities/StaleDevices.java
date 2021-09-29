/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StaleDevices {

  @JsonProperty
  private List<Long> staleDevices;

  public StaleDevices() {}

  public StaleDevices(List<Long> staleDevices) {
    this.staleDevices = staleDevices;
  }

}
