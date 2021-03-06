/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.providers;

import com.codahale.metrics.health.HealthCheck;
import org.smassarn.textsecuregcm.redis.FaultTolerantRedisCluster;

public class RedisClusterHealthCheck extends HealthCheck {

    private final FaultTolerantRedisCluster redisCluster;

    public RedisClusterHealthCheck(final FaultTolerantRedisCluster redisCluster) {
        this.redisCluster = redisCluster;
    }

    @Override
    protected Result check() {
        redisCluster.withCluster(connection -> connection.sync().upstream().commands().ping());
        return Result.healthy();
    }
}
