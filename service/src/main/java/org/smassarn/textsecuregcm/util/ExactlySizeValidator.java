/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;


public class ExactlySizeValidator implements ConstraintValidator<ExactlySize, String> {

  private int[] permittedSizes;

  @Override
  public void initialize(ExactlySize exactlySize) {
    this.permittedSizes = exactlySize.value();
  }

  @Override
  public boolean isValid(String object, ConstraintValidatorContext constraintContext) {
    int objectLength;

    if (object == null) objectLength = 0;
    else                objectLength = object.length();

    for (int permittedSize : permittedSizes) {
      if (permittedSize == objectLength) return true;
    }

    return false;
  }
}
