/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.controllers;

import io.dropwizard.auth.Auth;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.smassarn.textsecuregcm.auth.AuthenticatedAccount;
import org.smassarn.textsecuregcm.entities.StickerPackFormUploadAttributes;
import org.smassarn.textsecuregcm.entities.StickerPackFormUploadAttributes.StickerPackFormUploadItem;
import org.smassarn.textsecuregcm.limits.RateLimiters;
import org.smassarn.textsecuregcm.s3.PolicySigner;
import org.smassarn.textsecuregcm.s3.PostPolicyGenerator;
import org.smassarn.textsecuregcm.util.Constants;
import org.smassarn.textsecuregcm.util.Hex;
import org.smassarn.textsecuregcm.util.Pair;

@Path("/v1/sticker")
public class StickerController {

  private final RateLimiters        rateLimiters;
  private final PolicySigner        policySigner;
  private final PostPolicyGenerator policyGenerator;

  public StickerController(RateLimiters rateLimiters, String accessKey, String accessSecret, String region, String bucket) {
    this.rateLimiters    = rateLimiters;
    this.policySigner    = new PolicySigner(accessSecret, region);
    this.policyGenerator = new PostPolicyGenerator(region, bucket, accessKey);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/pack/form/{count}")
  public StickerPackFormUploadAttributes getStickersForm(@Auth AuthenticatedAccount auth,
      @PathParam("count") @Min(1) @Max(201) int stickerCount)
      throws RateLimitExceededException {
    rateLimiters.getStickerPackLimiter().validate(auth.getAccount().getUuid());

    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    String packId = generatePackId();
    String packLocation = "stickers/" + packId;
    String manifestKey = packLocation + "/manifest.proto";
    Pair<String, String> manifestPolicy = policyGenerator.createFor(now, manifestKey,
        Constants.MAXIMUM_STICKER_MANIFEST_SIZE_BYTES);
    String manifestSignature = policySigner.getSignature(now, manifestPolicy.second());
    StickerPackFormUploadItem manifest = new StickerPackFormUploadItem(-1, manifestKey, manifestPolicy.first(),
        "private", "AWS4-HMAC-SHA256",
        now.format(PostPolicyGenerator.AWS_DATE_TIME), manifestPolicy.second(), manifestSignature);

    List<StickerPackFormUploadItem> stickers = new LinkedList<>();

    for (int i = 0; i < stickerCount; i++) {
      String stickerKey = packLocation + "/full/" + i;
      Pair<String, String> stickerPolicy = policyGenerator.createFor(now, stickerKey,
          Constants.MAXIMUM_STICKER_SIZE_BYTES);
      String stickerSignature = policySigner.getSignature(now, stickerPolicy.second());
      stickers.add(new StickerPackFormUploadItem(i, stickerKey, stickerPolicy.first(), "private", "AWS4-HMAC-SHA256",
          now.format(PostPolicyGenerator.AWS_DATE_TIME), stickerPolicy.second(), stickerSignature));
    }

    return new StickerPackFormUploadAttributes(packId, manifest, stickers);
  }

  private String generatePackId() {
    byte[] object = new byte[16];
    new SecureRandom().nextBytes(object);

    return Hex.toStringCondensed(object);
  }

}
