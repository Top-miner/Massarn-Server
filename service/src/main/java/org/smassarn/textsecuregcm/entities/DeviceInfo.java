/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfo {
  @JsonProperty
  private long id;

  @JsonProperty
  private String name;

  @JsonProperty
  private long lastSeen;

  @JsonProperty
  private long created;

  public DeviceInfo(long id, String name, long lastSeen, long created) {
    this.id       = id;
    this.name     = name;
    this.lastSeen = lastSeen;
    this.created  = created;
  }
}
