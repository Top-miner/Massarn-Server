/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.auth;

import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import java.util.Optional;
import org.smassarn.textsecuregcm.storage.AccountsManager;

public class DisabledPermittedAccountAuthenticator extends BaseAccountAuthenticator implements
    Authenticator<BasicCredentials, DisabledPermittedAuthenticatedAccount> {

  public DisabledPermittedAccountAuthenticator(AccountsManager accountsManager) {
    super(accountsManager);
  }

  @Override
  public Optional<DisabledPermittedAuthenticatedAccount> authenticate(BasicCredentials credentials) {
    Optional<AuthenticatedAccount> account = super.authenticate(credentials, false);
    return account.map(DisabledPermittedAuthenticatedAccount::new);
  }
}
