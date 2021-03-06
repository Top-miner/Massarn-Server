/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.textsecuregcm.storage;

import java.util.Optional;
import org.smassarn.textsecuregcm.auth.StoredVerificationCode;

public class StoredVerificationCodeManager {

  private final VerificationCodeStore verificationCodeStore;

  public StoredVerificationCodeManager(final VerificationCodeStore verificationCodeStore) {
    this.verificationCodeStore = verificationCodeStore;
  }

  public void store(String number, StoredVerificationCode code) {
    verificationCodeStore.insert(number, code);
  }

  public void remove(String number) {
    verificationCodeStore.remove(number);
  }

  public Optional<StoredVerificationCode> getCodeForNumber(String number) {
    return verificationCodeStore.findForNumber(number);
  }
}
