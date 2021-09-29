/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.smassarn.textsecuregcm.crypto.Curve;
import org.smassarn.textsecuregcm.crypto.ECPrivateKey;
import org.smassarn.textsecuregcm.util.ByteArrayAdapter;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class UnidentifiedDeliveryConfiguration {

  @JsonProperty
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  @NotNull
  private byte[] certificate;

  @JsonProperty
  @JsonSerialize(using = ByteArrayAdapter.Serializing.class)
  @JsonDeserialize(using = ByteArrayAdapter.Deserializing.class)
  @NotNull
  @Size(min = 32, max = 32)
  private byte[] privateKey;

  @NotNull
  private int expiresDays;

  public byte[] getCertificate() {
    return certificate;
  }

  public ECPrivateKey getPrivateKey() {
    return Curve.decodePrivatePoint(privateKey);
  }

  public int getExpiresDays() {
    return expiresDays;
  }
}
