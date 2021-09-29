/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.smassarn.textsecuregcm.controllers;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.auth.Auth;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smassarn.textsecuregcm.auth.AuthenticatedAccount;
import org.smassarn.textsecuregcm.auth.AuthenticationCredentials;
import org.smassarn.textsecuregcm.auth.BasicAuthorizationHeader;
import org.smassarn.textsecuregcm.auth.DisabledPermittedAuthenticatedAccount;
import org.smassarn.textsecuregcm.auth.ExternalServiceCredentialGenerator;
import org.smassarn.textsecuregcm.auth.ExternalServiceCredentials;
import org.smassarn.textsecuregcm.auth.StoredRegistrationLock;
import org.smassarn.textsecuregcm.auth.StoredVerificationCode;
import org.smassarn.textsecuregcm.auth.TurnToken;
import org.smassarn.textsecuregcm.auth.TurnTokenGenerator;
import org.smassarn.textsecuregcm.configuration.dynamic.DynamicSignupCaptchaConfiguration;
import org.smassarn.textsecuregcm.entities.AccountAttributes;
import org.smassarn.textsecuregcm.entities.AccountCreationResult;
import org.smassarn.textsecuregcm.entities.ApnRegistrationId;
import org.smassarn.textsecuregcm.entities.DeviceName;
import org.smassarn.textsecuregcm.entities.GcmRegistrationId;
import org.smassarn.textsecuregcm.entities.RegistrationLock;
import org.smassarn.textsecuregcm.entities.RegistrationLockFailure;
import org.smassarn.textsecuregcm.limits.RateLimiters;
import org.smassarn.textsecuregcm.metrics.UserAgentTagUtil;
import org.smassarn.textsecuregcm.push.APNSender;
import org.smassarn.textsecuregcm.push.ApnMessage;
import org.smassarn.textsecuregcm.push.GCMSender;
import org.smassarn.textsecuregcm.push.GcmMessage;
import org.smassarn.textsecuregcm.recaptcha.RecaptchaClient;
import org.smassarn.textsecuregcm.sms.SmsSender;
import org.smassarn.textsecuregcm.sms.TwilioVerifyExperimentEnrollmentManager;
import org.smassarn.textsecuregcm.storage.AbusiveHostRule;
import org.smassarn.textsecuregcm.storage.AbusiveHostRules;
import org.smassarn.textsecuregcm.storage.Account;
import org.smassarn.textsecuregcm.storage.AccountsManager;
import org.smassarn.textsecuregcm.storage.Device;
import org.smassarn.textsecuregcm.storage.DynamicConfigurationManager;
import org.smassarn.textsecuregcm.storage.StoredVerificationCodeManager;
import org.smassarn.textsecuregcm.storage.UsernamesManager;
import org.smassarn.textsecuregcm.util.Constants;
import org.smassarn.textsecuregcm.util.ForwardedIpUtil;
import org.smassarn.textsecuregcm.util.Hex;
import org.smassarn.textsecuregcm.util.Util;
import org.smassarn.textsecuregcm.util.VerificationCode;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/v1/accounts")
public class AccountController {

  private final Logger         logger                 = LoggerFactory.getLogger(AccountController.class);
  private final MetricRegistry metricRegistry         = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          blockedHostMeter       = metricRegistry.meter(name(AccountController.class, "blocked_host"       ));
  private final Meter          filteredHostMeter      = metricRegistry.meter(name(AccountController.class, "filtered_host"      ));
  private final Meter          rateLimitedHostMeter   = metricRegistry.meter(name(AccountController.class, "rate_limited_host"  ));
  private final Meter          rateLimitedPrefixMeter = metricRegistry.meter(name(AccountController.class, "rate_limited_prefix"));
  private final Meter          captchaRequiredMeter   = metricRegistry.meter(name(AccountController.class, "captcha_required"   ));
  private final Meter          captchaSuccessMeter    = metricRegistry.meter(name(AccountController.class, "captcha_success"    ));
  private final Meter          captchaFailureMeter    = metricRegistry.meter(name(AccountController.class, "captcha_failure"    ));

  private static final String PUSH_CHALLENGE_COUNTER_NAME = name(AccountController.class, "pushChallenge");
  private static final String ACCOUNT_CREATE_COUNTER_NAME = name(AccountController.class, "create");
  private static final String ACCOUNT_VERIFY_COUNTER_NAME = name(AccountController.class, "verify");

  private static final String TWILIO_VERIFY_ERROR_COUNTER_NAME = name(AccountController.class, "twilioVerifyError");

  private static final String CHALLENGE_PRESENT_TAG_NAME = "present";
  private static final String CHALLENGE_MATCH_TAG_NAME = "matches";
  private static final String COUNTRY_CODE_TAG_NAME = "countryCode";
  private static final String VERFICATION_TRANSPORT_TAG_NAME = "transport";

  private static final String VERIFY_EXPERIMENT_TAG_NAME = "twilioVerify";

  private final StoredVerificationCodeManager      pendingAccounts;
  private final AccountsManager                    accounts;
  private final UsernamesManager                   usernames;
  private final AbusiveHostRules                   abusiveHostRules;
  private final RateLimiters                       rateLimiters;
  private final SmsSender                          smsSender;
  private final DynamicConfigurationManager        dynamicConfigurationManager;
  private final TurnTokenGenerator                 turnTokenGenerator;
  private final Map<String, Integer>               testDevices;
  private final RecaptchaClient recaptchaClient;
  private final GCMSender                          gcmSender;
  private final APNSender                          apnSender;
  private final ExternalServiceCredentialGenerator backupServiceCredentialGenerator;

  private final TwilioVerifyExperimentEnrollmentManager verifyExperimentEnrollmentManager;

  public AccountController(StoredVerificationCodeManager pendingAccounts,
                           AccountsManager accounts,
                           UsernamesManager usernames,
                           AbusiveHostRules abusiveHostRules,
                           RateLimiters rateLimiters,
                           SmsSender smsSenderFactory,
                           DynamicConfigurationManager dynamicConfigurationManager,
                           TurnTokenGenerator turnTokenGenerator,
                           Map<String, Integer> testDevices,
                           RecaptchaClient recaptchaClient,
                           GCMSender gcmSender,
                           APNSender apnSender,
                           ExternalServiceCredentialGenerator backupServiceCredentialGenerator,
                           TwilioVerifyExperimentEnrollmentManager verifyExperimentEnrollmentManager)
  {
    this.pendingAccounts                   = pendingAccounts;
    this.accounts                          = accounts;
    this.usernames                         = usernames;
    this.abusiveHostRules                  = abusiveHostRules;
    this.rateLimiters                      = rateLimiters;
    this.smsSender                         = smsSenderFactory;
    this.dynamicConfigurationManager       = dynamicConfigurationManager;
    this.testDevices                       = testDevices;
    this.turnTokenGenerator                = turnTokenGenerator;
    this.recaptchaClient = recaptchaClient;
    this.gcmSender                         = gcmSender;
    this.apnSender                         = apnSender;
    this.backupServiceCredentialGenerator  = backupServiceCredentialGenerator;
    this.verifyExperimentEnrollmentManager = verifyExperimentEnrollmentManager;
  }

  @Timed
  @GET
  @Path("/{type}/preauth/{token}/{number}")
  public Response getPreAuth(@PathParam("type")   String pushType,
                             @PathParam("token")  String pushToken,
                             @PathParam("number") String number,
                             @QueryParam("voip")  Optional<Boolean> useVoip)
  {
    if (!"apn".equals(pushType) && !"fcm".equals(pushType)) {
      return Response.status(400).build();
    }

    if (!Util.isValidNumber(number)) {
      return Response.status(400).build();
    }

    String                 pushChallenge          = generatePushChallenge();
    StoredVerificationCode storedVerificationCode = new StoredVerificationCode(null,
                                                                               System.currentTimeMillis(),
                                                                               pushChallenge,
                                                                               null);

    pendingAccounts.store(number, storedVerificationCode);

    if ("fcm".equals(pushType)) {
      gcmSender.sendMessage(new GcmMessage(pushToken, null, 0, GcmMessage.Type.CHALLENGE, Optional.of(storedVerificationCode.getPushCode())));
    } else if ("apn".equals(pushType)) {
      apnSender.sendMessage(new ApnMessage(pushToken, null, 0, useVoip.orElse(true), ApnMessage.Type.CHALLENGE, Optional.of(storedVerificationCode.getPushCode())));
    } else {
      throw new AssertionError();
    }

    return Response.ok().build();
  }

  @Timed
  @GET
  @Path("/{transport}/code/{number}")
  public Response createAccount(@PathParam("transport")         String transport,
                                @PathParam("number")            String number,
                                @HeaderParam("X-Forwarded-For") String forwardedFor,
                                @HeaderParam("User-Agent")      String userAgent,
                                @HeaderParam("Accept-Language") Optional<String> acceptLanguage,
                                @QueryParam("client")           Optional<String> client,
                                @QueryParam("captcha")          Optional<String> captcha,
                                @QueryParam("challenge")        Optional<String> pushChallenge)
      throws RateLimitExceededException, RetryLaterException
  {
    if (!Util.isValidNumber(number)) {
      logger.info("Invalid number: " + number);
      throw new WebApplicationException(Response.status(400).build());
    }

    String sourceHost = ForwardedIpUtil.getMostRecentProxy(forwardedFor).orElseThrow();

    Optional<StoredVerificationCode> storedChallenge = pendingAccounts.getCodeForNumber(number);
    CaptchaRequirement               requirement     = requiresCaptcha(number, transport, forwardedFor, sourceHost, captcha, storedChallenge, pushChallenge);

    if (requirement.isCaptchaRequired()) {
      captchaRequiredMeter.mark();

      if (requirement.isAutoBlock() && shouldAutoBlock(sourceHost)) {
        logger.info("Auto-block: {}", sourceHost);
        abusiveHostRules.setBlockedHost(sourceHost, "Auto-Block");
      }

      return Response.status(402).build();
    }

    try {
      switch (transport) {
        case "sms":
          rateLimiters.getSmsDestinationLimiter().validate(number);
          break;
        case "voice":
          rateLimiters.getVoiceDestinationLimiter().validate(number);
          rateLimiters.getVoiceDestinationDailyLimiter().validate(number);
          break;
        default:
          throw new WebApplicationException(Response.status(422).build());
      }
    } catch (RateLimitExceededException e) {
      if (!e.getRetryDuration().isNegative()) {
        throw new RetryLaterException(e);
      } else {
        throw e;
      }
    }

    VerificationCode       verificationCode       = generateVerificationCode(number);
    StoredVerificationCode storedVerificationCode = new StoredVerificationCode(verificationCode.getVerificationCode(),
        System.currentTimeMillis(),
        storedChallenge.map(StoredVerificationCode::getPushCode).orElse(null),
        storedChallenge.flatMap(StoredVerificationCode::getTwilioVerificationSid).orElse(null));

    pendingAccounts.store(number, storedVerificationCode);

    final List<Locale.LanguageRange> languageRanges;

    try {
      languageRanges = acceptLanguage.map(Locale.LanguageRange::parse).orElse(Collections.emptyList());
    } catch (final IllegalArgumentException e) {
      return Response.status(400).build();
    }

    final boolean enrolledInVerifyExperiment = verifyExperimentEnrollmentManager.isEnrolled(client, number, languageRanges, transport);
    final CompletableFuture<Optional<String>> sendVerificationWithTwilioVerifyFuture;

    if (testDevices.containsKey(number)) {
      // noop
      sendVerificationWithTwilioVerifyFuture = CompletableFuture.completedFuture(Optional.empty());
    } else if (transport.equals("sms")) {

      if (enrolledInVerifyExperiment) {
        sendVerificationWithTwilioVerifyFuture = smsSender.deliverSmsVerificationWithTwilioVerify(number, client, verificationCode.getVerificationCode(), languageRanges);
      } else {
        smsSender.deliverSmsVerification(number, client, verificationCode.getVerificationCodeDisplay());
        sendVerificationWithTwilioVerifyFuture = CompletableFuture.completedFuture(Optional.empty());
      }
    } else if (transport.equals("voice")) {

      if (enrolledInVerifyExperiment) {
        sendVerificationWithTwilioVerifyFuture = smsSender.deliverVoxVerificationWithTwilioVerify(number, verificationCode.getVerificationCode(), languageRanges);
      } else {
        smsSender.deliverVoxVerification(number, verificationCode.getVerificationCode(), languageRanges);
        sendVerificationWithTwilioVerifyFuture = CompletableFuture.completedFuture(Optional.empty());
      }

    } else {
      sendVerificationWithTwilioVerifyFuture = CompletableFuture.completedFuture(Optional.empty());
    }

    sendVerificationWithTwilioVerifyFuture.whenComplete((maybeVerificationSid, throwable) -> {
      if (throwable != null) {
        Metrics.counter(TWILIO_VERIFY_ERROR_COUNTER_NAME).increment();

        logger.warn("Error with Twilio Verify", throwable);
        return;
      }
      maybeVerificationSid.ifPresent(twilioVerificationSid -> {
        StoredVerificationCode storedVerificationCodeWithVerificationSid = new StoredVerificationCode(
            storedVerificationCode.getCode(),
            storedVerificationCode.getTimestamp(),
            storedVerificationCode.getPushCode(),
            twilioVerificationSid);
        pendingAccounts.store(number, storedVerificationCodeWithVerificationSid);
      });
    });

    // TODO Remove this meter when external dependencies have been resolved
    metricRegistry.meter(name(AccountController.class, "create", Util.getCountryCode(number))).mark();

    {
      final List<Tag> tags = new ArrayList<>();
      tags.add(Tag.of(COUNTRY_CODE_TAG_NAME, Util.getCountryCode(number)));
      tags.add(Tag.of(VERFICATION_TRANSPORT_TAG_NAME, transport));
      tags.add(UserAgentTagUtil.getPlatformTag(userAgent));
      tags.add(Tag.of(VERIFY_EXPERIMENT_TAG_NAME, String.valueOf(enrolledInVerifyExperiment)));

      Metrics.counter(ACCOUNT_CREATE_COUNTER_NAME, tags).increment();
    }

    return Response.ok().build();
  }

  @Timed
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/code/{verification_code}")
  public AccountCreationResult verifyAccount(@PathParam("verification_code") String verificationCode,
                                             @HeaderParam("Authorization") BasicAuthorizationHeader authorizationHeader,
                                             @HeaderParam("X-Massarn-Agent") String massarnAgent,
                                             @HeaderParam("User-Agent") String userAgent,
                                             @QueryParam("transfer") Optional<Boolean> availableForTransfer,
                                             @Valid AccountAttributes accountAttributes)
      throws RateLimitExceededException, InterruptedException {

    String number = authorizationHeader.getUsername();
    String password = authorizationHeader.getPassword();

    rateLimiters.getVerifyLimiter().validate(number);

    Optional<StoredVerificationCode> storedVerificationCode = pendingAccounts.getCodeForNumber(number);

    if (storedVerificationCode.isEmpty() || !storedVerificationCode.get().isValid(verificationCode)) {
      throw new WebApplicationException(Response.status(403).build());
    }

    storedVerificationCode.flatMap(StoredVerificationCode::getTwilioVerificationSid)
        .ifPresent(smsSender::reportVerificationSucceeded);

    Optional<Account>                    existingAccount           = accounts.get(number);
    Optional<StoredRegistrationLock>     existingRegistrationLock  = existingAccount.map(Account::getRegistrationLock);
    Optional<ExternalServiceCredentials> existingBackupCredentials = existingAccount.map(Account::getUuid)
                                                                                    .map(uuid -> backupServiceCredentialGenerator.generateFor(uuid.toString()));

    if (existingRegistrationLock.isPresent() && existingRegistrationLock.get().requiresClientRegistrationLock()) {
      rateLimiters.getVerifyLimiter().clear(number);

      if (!Util.isEmpty(accountAttributes.getRegistrationLock())) {
        rateLimiters.getPinLimiter().validate(number);
      }

      if (!existingRegistrationLock.get().verify(accountAttributes.getRegistrationLock())) {
        throw new WebApplicationException(Response.status(423)
                                                  .entity(new RegistrationLockFailure(existingRegistrationLock.get().getTimeRemaining(),
                                                                                      existingRegistrationLock.get().needsFailureCredentials() ? existingBackupCredentials.orElseThrow() : null))
                                                  .build());
      }

      rateLimiters.getPinLimiter().clear(number);
    }

    if (availableForTransfer.orElse(false) && existingAccount.map(Account::isTransferSupported).orElse(false)) {
      throw new WebApplicationException(Response.status(409).build());
    }

    Account account = accounts.create(number, password, massarnAgent, accountAttributes);

    {
      metricRegistry.meter(name(AccountController.class, "verify", Util.getCountryCode(number))).mark();

      final List<Tag> tags = new ArrayList<>();
      tags.add(Tag.of(COUNTRY_CODE_TAG_NAME, Util.getCountryCode(number)));
      tags.add(UserAgentTagUtil.getPlatformTag(userAgent));
      tags.add(Tag.of(VERIFY_EXPERIMENT_TAG_NAME, String.valueOf(storedVerificationCode.get().getTwilioVerificationSid().isPresent())));

      Metrics.counter(ACCOUNT_VERIFY_COUNTER_NAME, tags).increment();

      Metrics.timer(name(AccountController.class, "verifyDuration"), tags)
          .record(Instant.now().toEpochMilli() - storedVerificationCode.get().getTimestamp(), TimeUnit.MILLISECONDS);
    }

    return new AccountCreationResult(account.getUuid(), account.getNumber(), existingAccount.map(Account::isStorageSupported).orElse(false));
  }

  @Timed
  @GET
  @Path("/turn/")
  @Produces(MediaType.APPLICATION_JSON)
  public TurnToken getTurnToken(@Auth AuthenticatedAccount auth) throws RateLimitExceededException {
    rateLimiters.getTurnLimiter().validate(auth.getAccount().getUuid());
    return turnTokenGenerator.generate();
  }

  @Timed
  @PUT
  @Path("/gcm/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setGcmRegistrationId(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth,
      @Valid GcmRegistrationId registrationId) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();

    if (device.getGcmId() != null &&
        device.getGcmId().equals(registrationId.getGcmRegistrationId())) {
      return;
    }

    accounts.updateDevice(account, device.getId(), d -> {
      d.setApnId(null);
      d.setVoipApnId(null);
      d.setGcmId(registrationId.getGcmRegistrationId());
      d.setFetchesMessages(false);
    });
  }

  @Timed
  @DELETE
  @Path("/gcm/")
  public void deleteGcmRegistrationId(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();

    accounts.updateDevice(account, device.getId(), d -> {
      d.setGcmId(null);
      d.setFetchesMessages(false);
      d.setUserAgent("OWA");
    });
  }

  @Timed
  @PUT
  @Path("/apn/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setApnRegistrationId(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth,
      @Valid ApnRegistrationId registrationId) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();

    accounts.updateDevice(account, device.getId(), d -> {
      d.setApnId(registrationId.getApnRegistrationId());
      d.setVoipApnId(registrationId.getVoipRegistrationId());
      d.setGcmId(null);
      d.setFetchesMessages(false);
    });
  }

  @Timed
  @DELETE
  @Path("/apn/")
  public void deleteApnRegistrationId(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();

    accounts.updateDevice(account, device.getId(), d -> {
      d.setApnId(null);
      d.setFetchesMessages(false);
      if (d.getId() == 1) {
        d.setUserAgent("OWI");
      } else {
        d.setUserAgent("OWP");
      }
    });
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/registration_lock")
  public void setRegistrationLock(@Auth AuthenticatedAccount auth, @Valid RegistrationLock accountLock) {
    AuthenticationCredentials credentials = new AuthenticationCredentials(accountLock.getRegistrationLock());

    accounts.update(auth.getAccount(),
        a -> a.setRegistrationLock(credentials.getHashedAuthenticationToken(), credentials.getSalt()));
  }

  @Timed
  @DELETE
  @Path("/registration_lock")
  public void removeRegistrationLock(@Auth AuthenticatedAccount auth) {
    accounts.update(auth.getAccount(), a -> a.setRegistrationLock(null, null));
  }

  @Timed
  @PUT
  @Path("/name/")
  public void setName(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth, @Valid DeviceName deviceName) {
    Account account = disabledPermittedAuth.getAccount();
    Device device = disabledPermittedAuth.getAuthenticatedDevice();
    accounts.updateDevice(account, device.getId(), d -> d.setName(deviceName.getDeviceName()));
  }

  @Timed
  @DELETE
  @Path("/massarning_key")
  public void removeMassarningKey(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth) {
  }

  @Timed
  @PUT
  @Path("/attributes/")
  @Consumes(MediaType.APPLICATION_JSON)
  public void setAccountAttributes(@Auth DisabledPermittedAuthenticatedAccount disabledPermittedAuth,
      @HeaderParam("X-Massarn-Agent") String userAgent,
      @Valid AccountAttributes attributes) {
    Account account = disabledPermittedAuth.getAccount();
    long deviceId = disabledPermittedAuth.getAuthenticatedDevice().getId();

    // temporary: For deterministic updates during the DynamoDB migration, use a fully parameterized registration lock
    @Nullable final AuthenticationCredentials registrationLockCredentials =
        Util.isEmpty(attributes.getRegistrationLock()) ? null
            : new AuthenticationCredentials(attributes.getRegistrationLock());

    accounts.update(account, a -> {
      a.getDevice(deviceId).ifPresent(d -> {
        d.setFetchesMessages(attributes.getFetchesMessages());
        d.setName(attributes.getName());
        d.setLastSeen(Util.todayInMillis());
        d.setCapabilities(attributes.getCapabilities());
        d.setRegistrationId(attributes.getRegistrationId());
        d.setUserAgent(userAgent);
      });

      // temporary: for deterministic updates during the DynamoDB migration, use a fully parameterized registration lock
      // a.setRegistrationLockFromAttributes(attributes);
      if (registrationLockCredentials != null) {
        a.setRegistrationLock(registrationLockCredentials.getHashedAuthenticationToken(),
            registrationLockCredentials.getSalt());
      } else {
        a.setRegistrationLock(null, null);
      }

      a.setUnidentifiedAccessKey(attributes.getUnidentifiedAccessKey());
      a.setUnrestrictedUnidentifiedAccess(attributes.isUnrestrictedUnidentifiedAccess());
      a.setDiscoverableByPhoneNumber(attributes.isDiscoverableByPhoneNumber());
    });
  }

  @GET
  @Path("/me")
  @Produces(MediaType.APPLICATION_JSON)
  public AccountCreationResult getMe(@Auth AuthenticatedAccount auth) {
    return whoAmI(auth);
  }

  @GET
  @Path("/whoami")
  @Produces(MediaType.APPLICATION_JSON)
  public AccountCreationResult whoAmI(@Auth AuthenticatedAccount auth) {
    return new AccountCreationResult(auth.getAccount().getUuid(), auth.getAccount().getNumber(), auth.getAccount().isStorageSupported());
  }

  @DELETE
  @Path("/username")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteUsername(@Auth AuthenticatedAccount auth) {
    usernames.delete(auth.getAccount().getUuid());
  }

  @PUT
  @Path("/username/{username}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response setUsername(@Auth AuthenticatedAccount auth, @PathParam("username") String username)
      throws RateLimitExceededException {
    rateLimiters.getUsernameSetLimiter().validate(auth.getAccount().getUuid());

    if (username == null || username.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    username = username.toLowerCase();

    if (!username.matches("^[a-z_][a-z0-9_]+$")) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    if (!usernames.put(auth.getAccount().getUuid(), username)) {
      return Response.status(Response.Status.CONFLICT).build();
    }

    return Response.ok().build();
  }

  private CaptchaRequirement requiresCaptcha(String number, String transport, String forwardedFor,
                                             String                           sourceHost,
                                             Optional<String>                 captchaToken,
                                             Optional<StoredVerificationCode> storedVerificationCode,
                                             Optional<String>                 pushChallenge)
  {

    if (captchaToken.isPresent()) {
      boolean validToken = recaptchaClient.verify(captchaToken.get(), sourceHost);

      if (validToken) {
        captchaSuccessMeter.mark();
        return new CaptchaRequirement(false, false);
      } else {
        captchaFailureMeter.mark();
        return new CaptchaRequirement(true, false);
      }
    }

    final String countryCode = Util.getCountryCode(number);
    {
      final List<Tag> tags = new ArrayList<>();
      tags.add(Tag.of(COUNTRY_CODE_TAG_NAME, countryCode));

      try {
        if (pushChallenge.isPresent()) {
          tags.add(Tag.of(CHALLENGE_PRESENT_TAG_NAME, "true"));

          Optional<String> storedPushChallenge = storedVerificationCode.map(StoredVerificationCode::getPushCode);

          if (!pushChallenge.get().equals(storedPushChallenge.orElse(null))) {
            tags.add(Tag.of(CHALLENGE_MATCH_TAG_NAME, "false"));
            return new CaptchaRequirement(true, false);
          } else {
            tags.add(Tag.of(CHALLENGE_MATCH_TAG_NAME, "true"));
          }
        } else {
          tags.add(Tag.of(CHALLENGE_PRESENT_TAG_NAME, "false"));

          return new CaptchaRequirement(true, false);
        }
      } finally {
        Metrics.counter(PUSH_CHALLENGE_COUNTER_NAME, tags).increment();
      }
    }

    List<AbusiveHostRule> abuseRules = abusiveHostRules.getAbusiveHostRulesFor(sourceHost);

    for (AbusiveHostRule abuseRule : abuseRules) {
      if (abuseRule.isBlocked()) {
        logger.info("Blocked host: {}, {}, {} ({})", transport, number, sourceHost, forwardedFor);
        blockedHostMeter.mark();
        return new CaptchaRequirement(true, false);
      }

      if (!abuseRule.getRegions().isEmpty()) {
        if (abuseRule.getRegions().stream().noneMatch(number::startsWith)) {
          logger.info("Restricted host: {}, {}, {} ({})", transport, number, sourceHost, forwardedFor);
          filteredHostMeter.mark();
          return new CaptchaRequirement(true, false);
        }
      }
    }

    try {
      rateLimiters.getSmsVoiceIpLimiter().validate(sourceHost);
    } catch (RateLimitExceededException e) {
      logger.info("Rate limit exceeded: {}, {}, {} ({})", transport, number, sourceHost, forwardedFor);
      rateLimitedHostMeter.mark();
      return new CaptchaRequirement(true, true);
    }

    try {
      rateLimiters.getSmsVoicePrefixLimiter().validate(Util.getNumberPrefix(number));
    } catch (RateLimitExceededException e) {
      logger.info("Prefix rate limit exceeded: {}, {}, {} ({})", transport, number, sourceHost, forwardedFor);
      rateLimitedPrefixMeter.mark();
      return new CaptchaRequirement(true, true);
    }

    DynamicSignupCaptchaConfiguration signupCaptchaConfig = dynamicConfigurationManager.getConfiguration().getSignupCaptchaConfiguration();
    if (signupCaptchaConfig.getCountryCodes().contains(countryCode)) {
      return new CaptchaRequirement(true, false);
    }

    return new CaptchaRequirement(false, false);
  }

  @Timed
  @DELETE
  @Path("/me")
  public void deleteAccount(@Auth AuthenticatedAccount auth) throws InterruptedException {
    accounts.delete(auth.getAccount(), AccountsManager.DeletionReason.USER_REQUEST);
  }

  private boolean shouldAutoBlock(String sourceHost) {
    try {
      rateLimiters.getAutoBlockLimiter().validate(sourceHost);
    } catch (RateLimitExceededException e) {
      return true;
    }

    return false;
  }

  @VisibleForTesting protected
  VerificationCode generateVerificationCode(String number) {
    if (testDevices.containsKey(number)) {
      return new VerificationCode(testDevices.get(number));
    }

    SecureRandom random = new SecureRandom();
    int randomInt       = 100000 + random.nextInt(900000);
    return new VerificationCode(randomInt);
  }

  private String generatePushChallenge() {
    SecureRandom random    = new SecureRandom();
    byte[]       challenge = new byte[16];
    random.nextBytes(challenge);

    return Hex.toStringCondensed(challenge);
  }

  private static class CaptchaRequirement {
    private final boolean captchaRequired;
    private final boolean autoBlock;

    private CaptchaRequirement(boolean captchaRequired, boolean autoBlock) {
      this.captchaRequired = captchaRequired;
      this.autoBlock       = autoBlock;
    }

    boolean isCaptchaRequired() {
      return captchaRequired;
    }

    boolean isAutoBlock() {
      return autoBlock;
    }
  }
}