/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.proxy.s3.signature;

import alluxio.proxy.s3.S3ErrorCode;
import alluxio.proxy.s3.S3Exception;
import alluxio.proxy.s3.signature.AWSSignatureProcessor.LowerCaseKeyStringMap;
import alluxio.util.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.kerby.util.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.temporal.ChronoUnit;

/**
 * Stateless utility to create stringToSign, the base of the signature.
 */
public final class StringToSignProducer {

  public static final String X_AMZ_CONTENT_SHA256 = "X-Amz-Content-SHA256";
  public static final String X_AMAZ_DATE = "X-Amz-Date";
  private static final Logger LOG = LoggerFactory.getLogger(StringToSignProducer.class);
  private static final Charset UTF_8 = StandardCharsets.UTF_8;
  private static final String NEWLINE = "\n";
  private static final String HOST = "host";
  private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
  /**
   * Seconds in a week, which is the max expiration time Sig-v4 accepts.
   */
  private static final long PRESIGN_URL_MAX_EXPIRATION_SECONDS =
            60 * 60 * 24 * 7;
  public static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

  private StringToSignProducer() {
  }

  /**
   * Convert signature info to strToSign.
   *
   * @param signatureInfo
   * @param context
   * @return signature string
   * @throws Exception
   */
  public static String createSignatureBase(
      SignatureInfo signatureInfo,
      ContainerRequestContext context
  ) throws Exception {
    return createSignatureBase(signatureInfo,
        context.getUriInfo().getRequestUri().getScheme(),
        context.getMethod(),
        context.getUriInfo().getRequestUri().getPath(),
        LowerCaseKeyStringMap.fromHeaderMap(context.getHeaders()),
        fromMultiValueToSingleValueMap(
        context.getUriInfo().getQueryParameters()));
  }

  /**
   * Convert request info to strToSign.
   * @param signatureInfo
   * @param scheme
   * @param method
   * @param uri
   * @param headers
   * @param queryParams
   * @return signature string
   * @throws Exception
   */
  public static String createSignatureBase(
       SignatureInfo signatureInfo,
       String scheme,
       String method,
       String uri,
       LowerCaseKeyStringMap headers,
       Map<String, String> queryParams
  ) throws Exception {
    StringBuilder strToSign = new StringBuilder();
    // According to AWS sigv4 documentation, authorization header should be
    // in following format.
    // Authorization: algorithm Credential=access key ID/credential scope,
    // SignedHeaders=SignedHeaders, Signature=signature

    // Construct String to sign in below format.
    // StringToSign =
    //    Algorithm + \n +
    //    RequestDateTime + \n +
    //    CredentialScope + \n +
    //    HashedCanonicalRequest
    String credentialScope = signatureInfo.getCredentialScope();

    // If the absolute path is empty, use a forward slash (/)
    uri = (uri.trim().length() > 0) ? uri : "/";
    // Encode URI and preserve forward slashes
    strToSign.append(signatureInfo.getAlgorithm() + NEWLINE);
    strToSign.append(signatureInfo.getDateTime() + NEWLINE);
    strToSign.append(credentialScope + NEWLINE);

    String canonicalRequest = buildCanonicalRequest(
         scheme,
         method,
         uri,
         signatureInfo.getSignedHeaders(),
         headers,
         queryParams,
         !signatureInfo.isSignPayload());
    strToSign.append(hash(canonicalRequest));
    if (LOG.isDebugEnabled()) {
      LOG.debug("canonicalRequest:[{}]", canonicalRequest);
      LOG.debug("StringToSign:[{}]", strToSign);
    }

    return strToSign.toString();
  }

  /**
   * Convert MultivaluedMap to a single value map.
   *
   * @param queryParameters
   * @return a single value map
   */
  public static Map<String, String> fromMultiValueToSingleValueMap(
            MultivaluedMap<String, String> queryParameters) {
    Map<String, String> result = new HashMap<>();
    for (String key : queryParameters.keySet()) {
      result.put(key, queryParameters.getFirst(key));
    }
    return result;
  }

  /**
   * Compute a hash for provided string.
   * @param payload
   * @return hashed string
   * @throws NoSuchAlgorithmException
   */
  public static String hash(String payload) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(payload.getBytes(UTF_8));
    return Hex.encode(md.digest()).toLowerCase();
  }

  /**
   * Convert request info to a format string.
   * @param schema request schema
   * @param method request method
   * @param uri request uri
   * @param signedHeaders signed headers
   * @param headers request headers
   * @param queryParams request params
   * @param unsignedPayload whether or not the payload is unsigned
   * @return formatted string
   * @throws S3Exception
   */
  @VisibleForTesting
  public static String buildCanonicalRequest(
      String schema,
      String method,
      String uri,
      String signedHeaders,
      Map<String, String> headers,
      Map<String, String> queryParams,
      boolean unsignedPayload
  ) throws S3Exception {

    Iterable<String> parts = split("/", uri);
    List<String> encParts = new ArrayList<>();
    for (String p : parts) {
      encParts.add(urlEncode(p));
    }
    String canonicalUri = join("/", encParts);

    String canonicalQueryStr = getQueryParamString(queryParams);

    StringBuilder canonicalHeaders = new StringBuilder();

    for (String header : StringUtils.getStringCollection(signedHeaders, ";")) {
      canonicalHeaders.append(header.toLowerCase());
      canonicalHeaders.append(":");
      if (headers.containsKey(header)) {
        String headerValue = headers.get(header);
        canonicalHeaders.append(headerValue);
        canonicalHeaders.append(NEWLINE);

        // Set for testing purpose only to skip date and host validation.
        validateSignedHeader(schema, header, headerValue);
      } else {
        throw new RuntimeException("Header " + header + " not present in "
                + "request but requested to be signed.");
      }
    }

    String payloadHash;
    if (UNSIGNED_PAYLOAD.equals(headers.get(X_AMZ_CONTENT_SHA256))
            || unsignedPayload) {
      payloadHash = UNSIGNED_PAYLOAD;
    } else {
      payloadHash = headers.get(X_AMZ_CONTENT_SHA256);
    }
    String canonicalRequest = method + NEWLINE
            + canonicalUri + NEWLINE
            + canonicalQueryStr + NEWLINE
            + canonicalHeaders + NEWLINE
            + signedHeaders + NEWLINE
            + payloadHash;
    return canonicalRequest;
  }

  /**
   * String join that also works with empty strings.
   *
   * @return joined string
   */
  private static String join(String glue, List<String> parts) {
    StringBuilder result = new StringBuilder();
    boolean addSeparator = false;
    for (String p : parts) {
      if (addSeparator) {
        result.append(glue);
      }
      result.append(p);
      addSeparator = true;
    }
    return result.toString();
  }

  /**
   * Returns matching strings.
   *
   * @param regex Regular expression to split by
   * @param whole The string to split
   * @return pieces
   */
  private static Iterable<String> split(String regex, String whole) {
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(whole);
    List<String> result = new ArrayList<>();
    int pos = 0;
    while (m.find()) {
      result.add(whole.substring(pos, m.start()));
      pos = m.end();
    }
    result.add(whole.substring(pos));
    return result;
  }

  private static String urlEncode(String str) {
    try {
      return URLEncoder.encode(str, "UTF-8")
              .replaceAll("\\+", "%20")
              .replaceAll("%7E", "~");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getQueryParamString(Map<String, String> queryMap) {
    List<String> params = new ArrayList<>(queryMap.keySet());

    // Sort by name, then by value
    Collections.sort(params, (o1, o2) -> o1.equals(o2)
            ? queryMap.get(o1).compareTo(queryMap.get(o2))
            : o1.compareTo(o2));

    StringBuilder result = new StringBuilder();
    for (String p : params) {
      if (!p.equals("X-Amz-Signature")) {
        if (result.length() > 0) {
          result.append("&");
        }
        result.append(urlEncode(p));
        result.append('=');
        result.append(urlEncode(queryMap.get(p)));
      }
    }
    return result.toString();
  }

  @VisibleForTesting
  static void validateSignedHeader(
      String schema,
      String header,
      String headerValue
  ) throws S3Exception {
    switch (header) {
      case HOST:
        try {
          URI hostUri = new URI(schema + "://" + headerValue);
          InetAddress.getByName(hostUri.getHost());
        } catch (UnknownHostException | URISyntaxException e) {
          LOG.error("Host value mentioned in signed header is not valid. "
                  + "Host:{}", headerValue);
          throw new S3Exception(headerValue, S3ErrorCode.AUTHINFO_CREATION_ERROR);
        }
        break;
      case X_AMAZ_DATE:
        LocalDate date = LocalDate.parse(headerValue, TIME_FORMATTER);
        LocalDate now = LocalDate.now();
        if (date.isBefore(now.minus(PRESIGN_URL_MAX_EXPIRATION_SECONDS, ChronoUnit.SECONDS))
             || date.isAfter(now.plus(PRESIGN_URL_MAX_EXPIRATION_SECONDS, ChronoUnit.SECONDS))) {
          LOG.error("AWS date not in valid range. Request timestamp:{} should "
                 + "not be older than {} seconds.", headerValue,
                 PRESIGN_URL_MAX_EXPIRATION_SECONDS);
          throw new S3Exception(headerValue, S3ErrorCode.AUTHINFO_CREATION_ERROR);
        }
        break;
      case X_AMZ_CONTENT_SHA256:
        break;
      default:
        break;
    }
  }
}
