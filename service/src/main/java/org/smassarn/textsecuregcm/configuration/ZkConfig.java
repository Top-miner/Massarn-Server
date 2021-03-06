/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.smassarn.textsecuregcm.util.ByteArrayAdapter;

import javax.validation.constraints.NotNull;

public class ZkConfig {

  @JsonProperty
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  @NotNull
  private byte[] serverSecret;

  @JsonProperty
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  @NotNull
  private byte[] serverPublic;

  @JsonProperty
  @NotNull
  private Boolean enabled;

  public byte[] getServerSecret() {
    return serverSecret;
  }

  public byte[] getServerPublic() {
    return serverPublic;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
