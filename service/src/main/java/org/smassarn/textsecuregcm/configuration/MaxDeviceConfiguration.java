/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class MaxDeviceConfiguration {

  @JsonProperty
  @NotEmpty
  private String number;

  @JsonProperty
  @NotNull
  private int count;

  public String getNumber() {
    return number;
  }

  public int getCount() {
    return count;
  }

}
