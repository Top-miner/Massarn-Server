/*
 * Copyright 2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.auth;

import java.security.Principal;
import java.util.function.Supplier;
import javax.security.auth.Subject;
import org.smassarn.textsecuregcm.storage.Account;
import org.smassarn.textsecuregcm.storage.Device;
import org.smassarn.textsecuregcm.util.Pair;

public class AuthenticatedAccount implements Principal, AccountAndAuthenticatedDeviceHolder {

  private final Supplier<Pair<Account, Device>> accountAndDevice;

  public AuthenticatedAccount(final Supplier<Pair<Account, Device>> accountAndDevice) {
    this.accountAndDevice = accountAndDevice;
  }

  @Override
  public Account getAccount() {
    return accountAndDevice.get().first();
  }

  @Override
  public Device getAuthenticatedDevice() {
    return accountAndDevice.get().second();
  }

  // Principal implementation

  @Override
  public String getName() {
    return null;
  }

  @Override
  public boolean implies(final Subject subject) {
    return false;
  }
}
