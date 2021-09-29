/*
 * Copyright 2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.controllers;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import java.util.NoSuchElementException;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.smassarn.textsecuregcm.auth.AuthenticatedAccount;
import org.smassarn.textsecuregcm.entities.AnswerChallengeRequest;
import org.smassarn.textsecuregcm.entities.AnswerPushChallengeRequest;
import org.smassarn.textsecuregcm.entities.AnswerRecaptchaChallengeRequest;
import org.smassarn.textsecuregcm.limits.RateLimitChallengeManager;
import org.smassarn.textsecuregcm.push.NotPushRegisteredException;
import org.smassarn.textsecuregcm.util.ForwardedIpUtil;

@Path("/v1/challenge")
public class ChallengeController {

  private final RateLimitChallengeManager rateLimitChallengeManager;

  public ChallengeController(final RateLimitChallengeManager rateLimitChallengeManager) {
    this.rateLimitChallengeManager = rateLimitChallengeManager;
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response handleChallengeResponse(@Auth final AuthenticatedAccount auth,
      @Valid final AnswerChallengeRequest answerRequest,
      @HeaderParam("X-Forwarded-For") String forwardedFor) throws RetryLaterException {

    try {
      if (answerRequest instanceof AnswerPushChallengeRequest) {
        final AnswerPushChallengeRequest pushChallengeRequest = (AnswerPushChallengeRequest) answerRequest;

        rateLimitChallengeManager.answerPushChallenge(auth.getAccount(), pushChallengeRequest.getChallenge());
      } else if (answerRequest instanceof AnswerRecaptchaChallengeRequest) {
        try {

          final AnswerRecaptchaChallengeRequest recaptchaChallengeRequest = (AnswerRecaptchaChallengeRequest) answerRequest;
          final String mostRecentProxy = ForwardedIpUtil.getMostRecentProxy(forwardedFor).orElseThrow();

          rateLimitChallengeManager.answerRecaptchaChallenge(auth.getAccount(), recaptchaChallengeRequest.getCaptcha(),
              mostRecentProxy);

        } catch (final NoSuchElementException e) {
          return Response.status(400).build();
        }
      }
    } catch (final RateLimitExceededException e) {
      throw new RetryLaterException(e);
    }

    return Response.status(200).build();
  }

  @Timed
  @POST
  @Path("/push")
  public Response requestPushChallenge(@Auth final AuthenticatedAccount auth) {
    try {
      rateLimitChallengeManager.sendPushChallenge(auth.getAccount());
      return Response.status(200).build();
    } catch (final NotPushRegisteredException e) {
      return Response.status(404).build();
    }
  }
}
