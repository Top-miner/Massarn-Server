/*
 * Copyright 2013-2020 Massarn Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.smassarn.textsecuregcm.mappers;


import com.fasterxml.jackson.annotation.JsonProperty;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.smassarn.textsecuregcm.controllers.DeviceLimitExceededException;

@Provider
public class DeviceLimitExceededExceptionMapper implements ExceptionMapper<DeviceLimitExceededException> {
  @Override
  public Response toResponse(DeviceLimitExceededException exception) {
    return Response.status(411)
                   .entity(new DeviceLimitExceededDetails(exception.getCurrentDevices(),
                                                          exception.getMaxDevices()))
                   .build();
  }

  private static class DeviceLimitExceededDetails {
    @JsonProperty
    private int current;
    @JsonProperty
    private int max;

    public DeviceLimitExceededDetails(int current, int max) {
      this.current = current;
      this.max     = max;
    }
  }
}
