/*
 * Copyright 2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class AccountsDatabaseConfiguration extends DatabaseConfiguration {

    @JsonProperty
    @NotNull
    @Valid
    private RetryConfiguration keyOperationRetry = new RetryConfiguration();

    public RetryConfiguration getKeyOperationRetryConfiguration() {
        return keyOperationRetry;
    }
}
