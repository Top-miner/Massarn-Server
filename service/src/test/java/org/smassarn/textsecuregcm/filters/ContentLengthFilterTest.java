/*
 * Copyright 2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.filters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import org.smassarn.textsecuregcm.metrics.TrafficSource;

class ContentLengthFilterTest {

  @Test
  void testFilter() throws Exception {

    final ContentLengthFilter contentLengthFilter = new ContentLengthFilter(TrafficSource.WEBSOCKET);

    final ContainerRequestContext requestContext = mock(ContainerRequestContext.class);

    when(requestContext.getLength()).thenReturn(-1);
    when(requestContext.getLength()).thenReturn(Integer.MAX_VALUE);
    when(requestContext.getLength()).thenThrow(RuntimeException.class);

    contentLengthFilter.filter(requestContext);
    contentLengthFilter.filter(requestContext);
    contentLengthFilter.filter(requestContext);
  }
}