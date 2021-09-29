/*
 * Copyright 2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.limits;

import java.time.Duration;
import org.smassarn.textsecuregcm.storage.Account;

public class RateLimitChallengeException extends Exception {

  private final Account account;
  private final Duration retryAfter;

  public RateLimitChallengeException(final Account account, final Duration retryAfter) {
    this.account = account;
    this.retryAfter = retryAfter;
  }

  public Account getAccount() {
    return account;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }

}
