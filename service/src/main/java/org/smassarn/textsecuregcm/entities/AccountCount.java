/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.textsecuregcm.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountCount {

  @JsonProperty
  private int count;

  public AccountCount(int count) {
    this.count = count;
  }

  public AccountCount() {}

  public int getCount() {
    return count;
  }
}
