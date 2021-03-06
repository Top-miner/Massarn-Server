/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotEmpty;

public class RecaptchaConfiguration {

  @JsonProperty
  @NotEmpty
  private String secret;

  public String getSecret() {
    return secret;
  }

}
