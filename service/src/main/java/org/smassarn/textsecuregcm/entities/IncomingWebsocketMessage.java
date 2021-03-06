/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingWebsocketMessage {

  public static final int TYPE_ACKNOWLEDGE_MESSAGE = 1;
  public static final int TYPE_PING_MESSAGE        = 2;
  public static final int TYPE_PONG_MESSAGE        = 3;

  @JsonProperty
  protected int type;

  public IncomingWebsocketMessage() {}

  public IncomingWebsocketMessage(int type) {
    this.type = type;
  }

  public int getType() {
    return type;
  }
}
