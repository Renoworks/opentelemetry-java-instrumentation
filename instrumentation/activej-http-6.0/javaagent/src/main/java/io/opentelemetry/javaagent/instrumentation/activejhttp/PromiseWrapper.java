/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static io.opentelemetry.javaagent.instrumentation.activejhttp.ActivejHttpServerConnectionSingletons.instrumenter;

import io.activej.http.HttpError;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PromiseWrapper {

  public static Promise<HttpResponse> wrap(
      Promise<HttpResponse> promise, HttpRequest httpRequest, Context context, String traceparent) {
    if (promise != null) {
      AtomicReference<HttpResponse> response = new AtomicReference<>();
      promise.whenComplete(
          (httpResponse, exception) -> {
            httpResponse = createResponse(exception, traceparent, httpResponse);
            instrumenter().end(context, httpRequest, httpResponse, exception);
            response.set(httpResponse);
          });
      return Promise.of(response.get());
    } else {
      HttpResponse httpResponse =
          HttpResponse.notFound404().withHeader(HttpHeaders.of("traceparent"), traceparent).build();
      instrumenter().end(context, httpRequest, httpResponse, null);
      return Promise.of(httpResponse);
    }
  }

  static HttpResponse createResponse(
      Throwable throwable, String traceparent, HttpResponse httpResponse) {
    int code = 500;
    if (httpResponse != null) {
      code = httpResponse.getCode();
    } else if (throwable instanceof HttpError error) {
      code = error.getCode();
    }
    HttpResponse.Builder responseBuilder = HttpResponse.ofCode(code);
    if (httpResponse != null) {
      if (httpResponse.hasBody()) {
        responseBuilder.withBody(httpResponse.getBody());
      }
      for (Map.Entry<HttpHeader, HttpHeaderValue> entry : httpResponse.getHeaders()) {
        responseBuilder.withHeader(entry.getKey(), entry.getValue());
      }
    }
    if (throwable != null) {
      responseBuilder.withPlainText(throwable.getMessage());
    }
    return responseBuilder.withHeader(HttpHeaders.of("traceparent"), traceparent).build();
  }

  private PromiseWrapper() {}
}
