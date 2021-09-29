/*
 * Copyright 2013-2021 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.tests.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import java.io.IOException;
import java.util.Base64;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.massarn.zkgroup.InvalidInputException;
import org.massarn.zkgroup.ServerSecretParams;
import org.massarn.zkgroup.VerificationFailedException;
import org.massarn.zkgroup.auth.AuthCredential;
import org.massarn.zkgroup.auth.AuthCredentialResponse;
import org.massarn.zkgroup.auth.ClientZkAuthOperations;
import org.massarn.zkgroup.auth.ServerZkAuthOperations;
import org.smassarn.textsecuregcm.auth.AuthenticatedAccount;
import org.smassarn.textsecuregcm.auth.CertificateGenerator;
import org.smassarn.textsecuregcm.auth.DisabledPermittedAuthenticatedAccount;
import org.smassarn.textsecuregcm.auth.OptionalAccess;
import org.smassarn.textsecuregcm.controllers.CertificateController;
import org.smassarn.textsecuregcm.crypto.Curve;
import org.smassarn.textsecuregcm.entities.DeliveryCertificate;
import org.smassarn.textsecuregcm.entities.GroupCredentials;
import org.smassarn.textsecuregcm.entities.MessageProtos.SenderCertificate;
import org.smassarn.textsecuregcm.entities.MessageProtos.ServerCertificate;
import org.smassarn.textsecuregcm.tests.util.AuthHelper;
import org.smassarn.textsecuregcm.util.SystemMapper;
import org.smassarn.textsecuregcm.util.Util;

@ExtendWith(DropwizardExtensionsSupport.class)
class CertificateControllerTest {

  private static final String caPublicKey = "BWh+UOhT1hD8bkb+MFRvb6tVqhoG8YYGCzOd7mgjo8cV";
  private static final String caPrivateKey = "EO3Mnf0kfVlVnwSaqPoQnAxhnnGL1JTdXqktCKEe9Eo=";

  private static final String signingCertificate = "CiUIDBIhBbTz4h1My+tt+vw+TVscgUe/DeHS0W02tPWAWbTO2xc3EkD+go4bJnU0AcnFfbOLKoiBfCzouZtDYMOVi69rE7r4U9cXREEqOkUmU2WJBjykAxWPCcSTmVTYHDw7hkSp/puG";
  private static final String signingKey         = "ABOxG29xrfq4E7IrW11Eg7+HBbtba9iiS0500YoBjn4=";

  private static ServerSecretParams     serverSecretParams = ServerSecretParams.generate();
  private static CertificateGenerator   certificateGenerator;
  private static ServerZkAuthOperations serverZkAuthOperations;

  static {
    try {
      certificateGenerator   = new CertificateGenerator(Base64.getDecoder().decode(signingCertificate), Curve.decodePrivatePoint(Base64.getDecoder().decode(signingKey)), 1);
      serverZkAuthOperations = new ServerZkAuthOperations(serverSecretParams);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }


  private static final ResourceExtension resources = ResourceExtension.builder()
      .addProvider(AuthHelper.getAuthFilter())
      .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(
          ImmutableSet.of(AuthenticatedAccount.class, DisabledPermittedAuthenticatedAccount.class)))
      .setMapper(SystemMapper.getMapper())
      .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
      .addResource(new CertificateController(certificateGenerator, serverZkAuthOperations, true))
      .build();

  @Test
  void testValidCertificate() throws Exception {
    DeliveryCertificate certificateObject = resources.getJerseyTest()
                                                     .target("/v1/certificate/delivery")
                                                     .request()
                                                     .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                                     .get(DeliveryCertificate.class);


    SenderCertificate             certificateHolder = SenderCertificate.parseFrom(certificateObject.getCertificate());
    SenderCertificate.Certificate certificate       = SenderCertificate.Certificate.parseFrom(certificateHolder.getCertificate());

    ServerCertificate             serverCertificateHolder = certificate.getSigner();
    ServerCertificate.Certificate serverCertificate       = ServerCertificate.Certificate.parseFrom(serverCertificateHolder.getCertificate());

    assertTrue(Curve.verifySignature(Curve.decodePoint(serverCertificate.getKey().toByteArray(), 0), certificateHolder.getCertificate().toByteArray(), certificateHolder.getSignature().toByteArray()));
    assertTrue(Curve.verifySignature(Curve.decodePoint(Base64.getDecoder().decode(caPublicKey), 0), serverCertificateHolder.getCertificate().toByteArray(), serverCertificateHolder.getSignature().toByteArray()));

    assertEquals(certificate.getSender(), AuthHelper.VALID_NUMBER);
    assertEquals(certificate.getSenderDevice(), 1L);
    assertTrue(certificate.hasSenderUuid());
    assertEquals(AuthHelper.VALID_UUID.toString(), certificate.getSenderUuid());
    assertArrayEquals(certificate.getIdentityKey().toByteArray(), Base64.getDecoder().decode(AuthHelper.VALID_IDENTITY));
  }

  @Test
  void testValidCertificateWithUuid() throws Exception {
    DeliveryCertificate certificateObject = resources.getJerseyTest()
                                                     .target("/v1/certificate/delivery")
                                                     .queryParam("includeUuid", "true")
                                                     .request()
                                                     .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                                     .get(DeliveryCertificate.class);


    SenderCertificate             certificateHolder = SenderCertificate.parseFrom(certificateObject.getCertificate());
    SenderCertificate.Certificate certificate       = SenderCertificate.Certificate.parseFrom(certificateHolder.getCertificate());

    ServerCertificate             serverCertificateHolder = certificate.getSigner();
    ServerCertificate.Certificate serverCertificate       = ServerCertificate.Certificate.parseFrom(serverCertificateHolder.getCertificate());

    assertTrue(Curve.verifySignature(Curve.decodePoint(serverCertificate.getKey().toByteArray(), 0), certificateHolder.getCertificate().toByteArray(), certificateHolder.getSignature().toByteArray()));
    assertTrue(Curve.verifySignature(Curve.decodePoint(Base64.getDecoder().decode(caPublicKey), 0), serverCertificateHolder.getCertificate().toByteArray(), serverCertificateHolder.getSignature().toByteArray()));

    assertEquals(certificate.getSender(), AuthHelper.VALID_NUMBER);
    assertEquals(certificate.getSenderDevice(), 1L);
    assertEquals(certificate.getSenderUuid(), AuthHelper.VALID_UUID.toString());
    assertArrayEquals(certificate.getIdentityKey().toByteArray(), Base64.getDecoder().decode(AuthHelper.VALID_IDENTITY));
  }

  @Test
  void testValidCertificateWithUuidNoE164() throws Exception {
    DeliveryCertificate certificateObject = resources.getJerseyTest()
            .target("/v1/certificate/delivery")
            .queryParam("includeUuid", "true")
            .queryParam("includeE164", "false")
            .request()
            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
            .get(DeliveryCertificate.class);


    SenderCertificate             certificateHolder = SenderCertificate.parseFrom(certificateObject.getCertificate());
    SenderCertificate.Certificate certificate       = SenderCertificate.Certificate.parseFrom(certificateHolder.getCertificate());

    ServerCertificate             serverCertificateHolder = certificate.getSigner();
    ServerCertificate.Certificate serverCertificate       = ServerCertificate.Certificate.parseFrom(serverCertificateHolder.getCertificate());

    assertTrue(Curve.verifySignature(Curve.decodePoint(serverCertificate.getKey().toByteArray(), 0), certificateHolder.getCertificate().toByteArray(), certificateHolder.getSignature().toByteArray()));
    assertTrue(Curve.verifySignature(Curve.decodePoint(Base64.getDecoder().decode(caPublicKey), 0), serverCertificateHolder.getCertificate().toByteArray(), serverCertificateHolder.getSignature().toByteArray()));

    assertTrue(StringUtils.isBlank(certificate.getSender()));
    assertEquals(certificate.getSenderDevice(), 1L);
    assertEquals(certificate.getSenderUuid(), AuthHelper.VALID_UUID.toString());
    assertArrayEquals(certificate.getIdentityKey().toByteArray(), Base64.getDecoder().decode(AuthHelper.VALID_IDENTITY));
  }

  @Test
  void testBadAuthentication() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/delivery")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.INVALID_PASSWORD))
                                 .get();

    assertEquals(response.getStatus(), 401);
  }


  @Test
  void testNoAuthentication() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/delivery")
                                 .request()
                                 .get();

    assertEquals(response.getStatus(), 401);
  }


  @Test
  void testUnidentifiedAuthentication() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/delivery")
                                 .request()
                                 .header(OptionalAccess.UNIDENTIFIED, AuthHelper.getUnidentifiedAccessHeader("1234".getBytes()))
                                 .get();

    assertEquals(response.getStatus(), 401);
  }

  @Test
  void testDisabledAuthentication() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/delivery")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_UUID, AuthHelper.DISABLED_PASSWORD))
                                 .get();

    assertEquals(response.getStatus(), 401);
  }

  @Test
  void testGetSingleAuthCredential() throws InvalidInputException, VerificationFailedException {
    GroupCredentials credentials = resources.getJerseyTest()
                                            .target("/v1/certificate/group/" + Util.currentDaysSinceEpoch() + "/" + Util.currentDaysSinceEpoch())
                                            .request()
                                            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                            .get(GroupCredentials.class);

    assertThat(credentials.getCredentials().size()).isEqualTo(1);
    assertThat(credentials.getCredentials().get(0).getRedemptionTime()).isEqualTo(Util.currentDaysSinceEpoch());

    ClientZkAuthOperations clientZkAuthOperations = new ClientZkAuthOperations(serverSecretParams.getPublicParams());
    AuthCredential         credential             = clientZkAuthOperations.receiveAuthCredential(AuthHelper.VALID_UUID, Util.currentDaysSinceEpoch(), new AuthCredentialResponse(credentials.getCredentials().get(0).getCredential()));
  }

  @Test
  void testGetWeekLongAuthCredentials() throws InvalidInputException, VerificationFailedException {
    GroupCredentials credentials = resources.getJerseyTest()
                                            .target("/v1/certificate/group/" + Util.currentDaysSinceEpoch() + "/" + (Util.currentDaysSinceEpoch() + 7))
                                            .request()
                                            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                            .get(GroupCredentials.class);

    assertThat(credentials.getCredentials().size()).isEqualTo(8);

    for (int i=0;i<=7;i++) {
      assertThat(credentials.getCredentials().get(i).getRedemptionTime()).isEqualTo(Util.currentDaysSinceEpoch() + i);

      ClientZkAuthOperations clientZkAuthOperations = new ClientZkAuthOperations(serverSecretParams.getPublicParams());
      AuthCredential         credential             = clientZkAuthOperations.receiveAuthCredential(AuthHelper.VALID_UUID, Util.currentDaysSinceEpoch() + i , new AuthCredentialResponse(credentials.getCredentials().get(i).getCredential()));
    }
  }

  @Test
  void testTooManyDaysOut() throws InvalidInputException {
    Response response = resources.getJerseyTest()
                                            .target("/v1/certificate/group/" + Util.currentDaysSinceEpoch() + "/" + (Util.currentDaysSinceEpoch() + 8))
                                            .request()
                                            .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                            .get();

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testBackwardsInTime() throws InvalidInputException {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/group/" + (Util.currentDaysSinceEpoch() - 1) + "/" + (Util.currentDaysSinceEpoch() + 7))
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.VALID_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  void testBadAuth() throws InvalidInputException {
    Response response = resources.getJerseyTest()
                                 .target("/v1/certificate/group/" + Util.currentDaysSinceEpoch() + "/" + (Util.currentDaysSinceEpoch() + 7))
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_UUID, AuthHelper.INVALID_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }
}