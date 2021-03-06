/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.websocket.logging.layout.converters;

import org.smassarn.websocket.logging.WebsocketEvent;

public class ContentLengthConverter extends WebSocketEventConverter {
  @Override
  public String convert(WebsocketEvent event) {
    if (event.getContentLength() == WebsocketEvent.SENTINEL) {
      return WebsocketEvent.NA;
    } else {
      return Long.toString(event.getContentLength());
    }
  }
}
