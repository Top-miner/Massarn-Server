/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.util.logging;

import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

class RequestLogEnabledFilter<E> extends Filter<E> {

    private volatile boolean requestLoggingEnabled = false;

    @Override
    public FilterReply decide(final E event) {
        return requestLoggingEnabled ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    public void setRequestLoggingEnabled(final boolean requestLoggingEnabled) {
        this.requestLoggingEnabled = requestLoggingEnabled;
    }
}
