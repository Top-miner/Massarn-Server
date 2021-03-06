/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.tests.util;

import com.amazonaws.HttpMethod;
import org.junit.Test;
import org.smassarn.textsecuregcm.s3.UrlSigner;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlSignerTest {

  @Test
  public void testTransferAcceleration() {
    UrlSigner signer = new UrlSigner("foo", "bar", "attachments-test");
    URL url = signer.getPreSignedUrl(1234, HttpMethod.GET, false);

    assertThat(url).hasHost("attachments-test.s3-accelerate.amazonaws.com");
  }

  @Test
  public void testTransferUnaccelerated() {
    UrlSigner signer = new UrlSigner("foo", "bar", "attachments-test");
    URL url = signer.getPreSignedUrl(1234, HttpMethod.GET, true);

    assertThat(url).hasHost("s3.amazonaws.com");
  }

}
