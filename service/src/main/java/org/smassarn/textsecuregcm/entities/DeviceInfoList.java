/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class DeviceInfoList {

  @JsonProperty
  private List<DeviceInfo> devices;

  public DeviceInfoList(List<DeviceInfo> devices) {
    this.devices = devices;
  }
}
