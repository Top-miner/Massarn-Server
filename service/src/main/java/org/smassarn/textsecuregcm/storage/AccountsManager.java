/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.textsecuregcm.storage;


import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smassarn.textsecuregcm.auth.AuthenticationCredentials;
import org.smassarn.textsecuregcm.controllers.AccountController;
import org.smassarn.textsecuregcm.entities.AccountAttributes;
import org.smassarn.textsecuregcm.redis.FaultTolerantRedisCluster;
import org.smassarn.textsecuregcm.securebackup.SecureBackupClient;
import org.smassarn.textsecuregcm.securestorage.SecureStorageClient;
import org.smassarn.textsecuregcm.sqs.DirectoryQueue;
import org.smassarn.textsecuregcm.util.Constants;
import org.smassarn.textsecuregcm.util.SystemMapper;
import org.smassarn.textsecuregcm.util.Util;

public class AccountsManager {

  private static final MetricRegistry metricRegistry   = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private static final Timer          createTimer      = metricRegistry.timer(name(AccountsManager.class, "create"     ));
  private static final Timer          updateTimer      = metricRegistry.timer(name(AccountsManager.class, "update"     ));
  private static final Timer          getByNumberTimer = metricRegistry.timer(name(AccountsManager.class, "getByNumber"));
  private static final Timer          getByUuidTimer   = metricRegistry.timer(name(AccountsManager.class, "getByUuid"  ));
  private static final Timer          deleteTimer      = metricRegistry.timer(name(AccountsManager.class, "delete"));

  // TODO Remove this meter when external dependencies have been resolved
  // Note that this is deliberately namespaced to `AccountController` for metric continuity.
  private static final Meter newUserMeter = metricRegistry.meter(name(AccountController.class, "brand_new_user"));

  private static final Timer redisSetTimer       = metricRegistry.timer(name(AccountsManager.class, "redisSet"      ));
  private static final Timer redisNumberGetTimer = metricRegistry.timer(name(AccountsManager.class, "redisNumberGet"));
  private static final Timer redisUuidGetTimer   = metricRegistry.timer(name(AccountsManager.class, "redisUuidGet"  ));
  private static final Timer redisDeleteTimer    = metricRegistry.timer(name(AccountsManager.class, "redisDelete"   ));

  private static final String CREATE_COUNTER_NAME       = name(AccountsManager.class, "createCounter");
  private static final String DELETE_COUNTER_NAME       = name(AccountsManager.class, "deleteCounter");
  private static final String COUNTRY_CODE_TAG_NAME     = "country";
  private static final String DELETION_REASON_TAG_NAME  = "reason";

  private final Logger logger = LoggerFactory.getLogger(AccountsManager.class);

  private final Accounts accounts;
  private final FaultTolerantRedisCluster cacheCluster;
  private final DeletedAccountsManager deletedAccountsManager;
  private final DirectoryQueue            directoryQueue;
  private final KeysDynamoDb              keysDynamoDb;
  private final MessagesManager messagesManager;
  private final UsernamesManager usernamesManager;
  private final ProfilesManager           profilesManager;
  private final StoredVerificationCodeManager pendingAccounts;
  private final SecureStorageClient       secureStorageClient;
  private final SecureBackupClient        secureBackupClient;
  private final ObjectMapper              mapper;

  public enum DeletionReason {
    ADMIN_DELETED("admin"),
    EXPIRED      ("expired"),
    USER_REQUEST ("userRequest");

    private final String tagValue;

    DeletionReason(final String tagValue) {
      this.tagValue = tagValue;
    }
  }

  public AccountsManager(Accounts accounts, FaultTolerantRedisCluster cacheCluster,
      final DeletedAccountsManager deletedAccountsManager,
      final DirectoryQueue directoryQueue,
      final KeysDynamoDb keysDynamoDb, final MessagesManager messagesManager,
      final UsernamesManager usernamesManager,
      final ProfilesManager profilesManager,
      final StoredVerificationCodeManager pendingAccounts,
      final SecureStorageClient secureStorageClient,
      final SecureBackupClient secureBackupClient) {
    this.accounts = accounts;
    this.cacheCluster = cacheCluster;
    this.deletedAccountsManager = deletedAccountsManager;
    this.directoryQueue = directoryQueue;
    this.keysDynamoDb = keysDynamoDb;
    this.messagesManager = messagesManager;
    this.usernamesManager = usernamesManager;
    this.profilesManager = profilesManager;
    this.pendingAccounts = pendingAccounts;
    this.secureStorageClient = secureStorageClient;
    this.secureBackupClient  = secureBackupClient;
    this.mapper              = SystemMapper.getMapper();

  }

  public Account create(final String number,
      final String password,
      final String massarnAgent,
      final AccountAttributes accountAttributes) throws InterruptedException {

    try (Timer.Context ignored = createTimer.time()) {
      final Account account = new Account();

      deletedAccountsManager.lockAndTake(number, maybeRecentlyDeletedUuid -> {
        Device device = new Device();
        device.setId(Device.MASTER_ID);
        device.setAuthenticationCredentials(new AuthenticationCredentials(password));
        device.setFetchesMessages(accountAttributes.getFetchesMessages());
        device.setRegistrationId(accountAttributes.getRegistrationId());
        device.setName(accountAttributes.getName());
        device.setCapabilities(accountAttributes.getCapabilities());
        device.setCreated(System.currentTimeMillis());
        device.setLastSeen(Util.todayInMillis());
        device.setUserAgent(massarnAgent);

        account.setNumber(number);
        account.setUuid(maybeRecentlyDeletedUuid.orElseGet(UUID::randomUUID));
        account.addDevice(device);
        account.setRegistrationLockFromAttributes(accountAttributes);
        account.setUnidentifiedAccessKey(accountAttributes.getUnidentifiedAccessKey());
        account.setUnrestrictedUnidentifiedAccess(accountAttributes.isUnrestrictedUnidentifiedAccess());
        account.setDiscoverableByPhoneNumber(accountAttributes.isDiscoverableByPhoneNumber());

        final UUID originalUuid = account.getUuid();

        boolean freshUser = dynamoCreate(account);

        // create() sometimes updates the UUID, if there was a number conflict.
        // for metrics, we want secondary to run with the same original UUID
        final UUID actualUuid = account.getUuid();

        redisSet(account);

        pendingAccounts.remove(number);

        // In terms of previously-existing accounts, there are three possible cases:
        //
        // 1. This is a completely new account; there was no pre-existing account and no recently-deleted account
        // 2. This is a re-registration of an existing account. The storage layer will update the existing account in
        //    place to match the account record created above, and will update the UUID of the newly-created account
        //    instance to match the stored account record (i.e. originalUuid != actualUuid).
        // 3. This is a re-registration of a recently-deleted account, in which case maybeRecentlyDeletedUuid is
        //    present.
        //
        // All cases are mutually-exclusive. In the first case, we don't need to do anything. In the third, we can be
        // confident that everything has already been deleted. In the second case, though, we're taking over an existing
        // account and need to clear out messages and keys that may have been stored for the old account.
        if (!originalUuid.equals(actualUuid)) {
          messagesManager.clear(actualUuid);
          keysDynamoDb.delete(actualUuid);
          profilesManager.deleteAll(actualUuid);
        }

        final Tags tags;

        if (freshUser) {
          tags = Tags.of("type", "new");
          newUserMeter.mark();
        } else if (!originalUuid.equals(actualUuid)) {
          tags = Tags.of("type", "re-registration");
        } else {
          tags = Tags.of("type", "recently-deleted");
        }

        Metrics.counter(CREATE_COUNTER_NAME, tags).increment();

        if (!account.isDiscoverableByPhoneNumber()) {
          // The newly-created account has explicitly opted out of discoverability
          directoryQueue.deleteAccount(account);
        }
      });

      return account;
    }
  }

  public Account update(Account account, Consumer<Account> updater) {

    return update(account, a -> {
      updater.accept(a);
      // assume that all updaters passed to the public method actually modify the account
      return true;
    });
  }

  /**
   * Specialized version of {@link #updateDevice(Account, long, Consumer)} that minimizes potentially contentious and
   * redundant updates of {@code device.lastSeen}
   */
  public Account updateDeviceLastSeen(Account account, Device device, final long lastSeen) {

    return update(account, a -> {

      final Optional<Device> maybeDevice = a.getDevice(device.getId());

      return maybeDevice.map(d -> {
        if (d.getLastSeen() >= lastSeen) {
          return false;
        }

        d.setLastSeen(lastSeen);

        return true;

      }).orElse(false);
    });
  }

  /**
   * @param account account to update
   * @param updater must return {@code true} if the account was actually updated
   */
  private Account update(Account account, Function<Account, Boolean> updater) {

    final boolean wasVisibleBeforeUpdate = account.shouldBeVisibleInDirectory();

    final Account updatedAccount;

    try (Timer.Context ignored = updateTimer.time()) {

      redisDelete(account);

      final UUID uuid = account.getUuid();

      updatedAccount = updateWithRetries(account, updater, this::dynamoUpdate, () -> dynamoGet(uuid).get());

      redisSet(updatedAccount);
    }

    final boolean isVisibleAfterUpdate = updatedAccount.shouldBeVisibleInDirectory();

    if (wasVisibleBeforeUpdate != isVisibleAfterUpdate) {
      directoryQueue.refreshAccount(updatedAccount);
    }

    return updatedAccount;
  }

  private Account updateWithRetries(Account account, Function<Account, Boolean> updater, Consumer<Account> persister,
      Supplier<Account> retriever) {

    if (!updater.apply(account)) {
      return account;
    }

    final int maxTries = 10;
    int tries = 0;

    while (tries < maxTries) {

      try {
        persister.accept(account);

        final Account updatedAccount;
        try {
          updatedAccount = mapper.readValue(mapper.writeValueAsBytes(account), Account.class);
          updatedAccount.setUuid(account.getUuid());
        } catch (final IOException e) {
          // this should really, truly, never happen
          throw new IllegalArgumentException(e);
        }

        account.markStale();

        return updatedAccount;
      } catch (final ContestedOptimisticLockException e) {
        tries++;
        account = retriever.get();

        if (!updater.apply(account)) {
          return account;
        }
      }
    }

    throw new OptimisticLockRetryLimitExceededException();
  }

  public Account updateDevice(Account account, long deviceId, Consumer<Device> deviceUpdater) {
    return update(account, a -> {
      a.getDevice(deviceId).ifPresent(deviceUpdater);
      // assume that all updaters passed to the public method actually modify the device
      return true;
    });
  }

  public Optional<Account> get(String number) {
    try (Timer.Context ignored = getByNumberTimer.time()) {
      Optional<Account> account = redisGet(number);

      if (account.isEmpty()) {
        account = dynamoGet(number);
        account.ifPresent(this::redisSet);
      }

      return account;
    }
  }

  public Optional<Account> get(UUID uuid) {
    try (Timer.Context ignored = getByUuidTimer.time()) {
      Optional<Account> account = redisGet(uuid);

      if (account.isEmpty()) {
        account = dynamoGet(uuid);
        account.ifPresent(this::redisSet);
      }

      return account;
    }
  }

  public AccountCrawlChunk getAllFromDynamo(int length) {
    return accounts.getAllFromStart(length);
  }

  public AccountCrawlChunk getAllFromDynamo(UUID uuid, int length) {
    return accounts.getAllFrom(uuid, length);
  }

  public void delete(final Account account, final DeletionReason deletionReason) throws InterruptedException {
    try (final Timer.Context ignored = deleteTimer.time()) {
      deletedAccountsManager.lockAndPut(account.getNumber(), () -> {
        final CompletableFuture<Void> deleteStorageServiceDataFuture = secureStorageClient.deleteStoredData(account.getUuid());
        final CompletableFuture<Void> deleteBackupServiceDataFuture = secureBackupClient.deleteBackups(account.getUuid());

        usernamesManager.delete(account.getUuid());
        directoryQueue.deleteAccount(account);
        profilesManager.deleteAll(account.getUuid());
        keysDynamoDb.delete(account.getUuid());
        messagesManager.clear(account.getUuid());

        deleteStorageServiceDataFuture.join();
        deleteBackupServiceDataFuture.join();

        redisDelete(account);
        dynamoDelete(account);

        return account.getUuid();
      });
    } catch (final RuntimeException | InterruptedException e) {
      logger.warn("Failed to delete account", e);
      throw e;
    }

    Metrics.counter(DELETE_COUNTER_NAME,
        COUNTRY_CODE_TAG_NAME, Util.getCountryCode(account.getNumber()),
        DELETION_REASON_TAG_NAME, deletionReason.tagValue)
        .increment();
  }

  private String getAccountMapKey(String number) {
    return "AccountMap::" + number;
  }

  private String getAccountEntityKey(UUID uuid) {
    return "Account3::" + uuid.toString();
  }

  private void redisSet(Account account) {
    try (Timer.Context ignored = redisSetTimer.time()) {
      final String accountJson = mapper.writeValueAsString(account);

      cacheCluster.useCluster(connection -> {
        final RedisAdvancedClusterCommands<String, String> commands = connection.sync();

        commands.set(getAccountMapKey(account.getNumber()), account.getUuid().toString());
        commands.set(getAccountEntityKey(account.getUuid()), accountJson);
      });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Optional<Account> redisGet(String number) {
    try (Timer.Context ignored = redisNumberGetTimer.time()) {
      final String uuid = cacheCluster.withCluster(connection -> connection.sync().get(getAccountMapKey(number)));

      if (uuid != null) return redisGet(UUID.fromString(uuid));
      else              return Optional.empty();
    } catch (IllegalArgumentException e) {
      logger.warn("Deserialization error", e);
      return Optional.empty();
    } catch (RedisException e) {
      logger.warn("Redis failure", e);
      return Optional.empty();
    }
  }

  private Optional<Account> redisGet(UUID uuid) {
    try (Timer.Context ignored = redisUuidGetTimer.time()) {
      final String json = cacheCluster.withCluster(connection -> connection.sync().get(getAccountEntityKey(uuid)));

      if (json != null) {
        Account account = mapper.readValue(json, Account.class);
        account.setUuid(uuid);

        return Optional.of(account);
      }

      return Optional.empty();
    } catch (IOException e) {
      logger.warn("Deserialization error", e);
      return Optional.empty();
    } catch (RedisException e) {
      logger.warn("Redis failure", e);
      return Optional.empty();
    }
  }

  private void redisDelete(final Account account) {
    try (final Timer.Context ignored = redisDeleteTimer.time()) {
      cacheCluster.useCluster(connection -> connection.sync()
          .del(getAccountMapKey(account.getNumber()), getAccountEntityKey(account.getUuid())));
    }
  }

  private Optional<Account> dynamoGet(String number) {
    return accounts.get(number);
  }

  private Optional<Account> dynamoGet(UUID uuid) {
    return accounts.get(uuid);
  }

  private boolean dynamoCreate(Account account) {
    return accounts.create(account);
  }

  private void dynamoUpdate(Account account) {
    accounts.update(account);
  }

  private void dynamoDelete(final Account account) {
    accounts.delete(account.getUuid());
  }

}