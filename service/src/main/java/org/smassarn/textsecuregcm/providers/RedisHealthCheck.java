/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.textsecuregcm.providers;

import com.codahale.metrics.health.HealthCheck;
import org.smassarn.textsecuregcm.redis.ReplicatedJedisPool;

import redis.clients.jedis.Jedis;

public class RedisHealthCheck extends HealthCheck {

  private final ReplicatedJedisPool clientPool;

  public RedisHealthCheck(ReplicatedJedisPool clientPool) {
    this.clientPool = clientPool;
  }

  @Override
  protected Result check() throws Exception {
    try (Jedis client = clientPool.getWriteResource()) {
      client.set("HEALTH", "test");

      if (!"test".equals(client.get("HEALTH"))) {
        return Result.unhealthy("fetch failed");
      }

      return Result.healthy();
    }
  }
}
