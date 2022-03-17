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

package alluxio.proxy.s3;

import javax.ws.rs.core.Response;

/**
 * Error codes defined in http://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html with
 * API version 2006-03-01. Some error codes are customized.
 */
public class S3ErrorCode {
  /**
   * Error code names used in {@link S3ErrorCode}.
   */
  public static final class Name {
    public static final String BAD_DIGEST = "BadDigest";
    public static final String BUCKET_ALREADY_EXISTS = "BucketAlreadyExists";
    public static final String BUCKET_NOT_EMPTY = "BucketNotEmpty";
    public static final String INTERNAL_ERROR = "InternalError";
    public static final String INVALID_BUCKET_NAME = "InvalidBucketName";
    public static final String NO_SUCH_BUCKET = "NoSuchBucket";
    public static final String NO_SUCH_KEY = "NoSuchKey";
    public static final String NO_SUCH_UPLOAD = "NoSuchUpload";
    public static final String PRECONDITION_FAILED = "PreconditionFailed";
    public static final String INVALID_CONTINUATION_TOKEN = "InvalidContinuationToken";
    public static final String UPLOAD_ALREADY_EXISTS = "UploadAlreadyExists";
    public static final String AUTHORIZATION_HEADER_MALFORMED = "AuthorizationHeaderMalformed";
    public static final String AUTHINFO_CREATION_ERROR = "AuthInfoCreationError";
    public static final String ACCESS_DENIED_ERROR = "AccessDenied";
    public static final String INVALID_IDENTIFIER = "InvalidIdentifier";
    public static final String NOT_FOUND_CUSTOMIZED_SECRET_MANAGER
            = "NotProvidCustomSecretManager";

    private Name() {
    } // prevents instantiation
  }

  //
  // Official error codes.
  //
  public static final S3ErrorCode BAD_DIGEST = new S3ErrorCode(
      Name.BAD_DIGEST,
      "The Content-MD5 you specified did not match what we received.",
      Response.Status.BAD_REQUEST);
  public static final S3ErrorCode BUCKET_ALREADY_EXISTS = new S3ErrorCode(
      Name.BUCKET_ALREADY_EXISTS,
      "The requested bucket name already exists",
      Response.Status.CONFLICT);
  public static final S3ErrorCode BUCKET_NOT_EMPTY = new S3ErrorCode(
      Name.BUCKET_NOT_EMPTY,
      "The bucket you tried to delete is not empty",
      Response.Status.CONFLICT);
  public static final S3ErrorCode INVALID_BUCKET_NAME = new S3ErrorCode(
      Name.INVALID_BUCKET_NAME,
      "The specified bucket name is invalid",
      Response.Status.BAD_REQUEST);
  public static final S3ErrorCode INTERNAL_ERROR = new S3ErrorCode(
      Name.INTERNAL_ERROR,
      "We encountered an internal error. Please try again.",
      Response.Status.INTERNAL_SERVER_ERROR);
  public static final S3ErrorCode NO_SUCH_BUCKET = new S3ErrorCode(
      Name.NO_SUCH_BUCKET,
      "The specified bucket or prefix does not exist",
      Response.Status.NOT_FOUND);
  public static final S3ErrorCode NO_SUCH_KEY = new S3ErrorCode(
      Name.NO_SUCH_KEY,
      "The specified key does not exist",
      Response.Status.NOT_FOUND);
  public static final S3ErrorCode NO_SUCH_UPLOAD = new S3ErrorCode(
      Name.NO_SUCH_UPLOAD,
      "The specified multipart upload does not exist. "
      + "The upload ID might be invalid, or the multipart upload might have been aborted "
      + "or completed.",
      Response.Status.NOT_FOUND);
  public static final S3ErrorCode PRECONDITION_FAILED = new S3ErrorCode(
      Name.PRECONDITION_FAILED,
      "At least one of the preconditions did not hold",
      Response.Status.PRECONDITION_FAILED);
  public static final S3ErrorCode INVALID_CONTINUATION_TOKEN = new S3ErrorCode(
      Name.INVALID_CONTINUATION_TOKEN,
      "The continuation token provided is incorrect",
      Response.Status.BAD_REQUEST);
  public static final S3ErrorCode UPLOAD_ALREADY_EXISTS = new S3ErrorCode(
      Name.UPLOAD_ALREADY_EXISTS,
      "The specified multipart upload already exits",
      Response.Status.CONFLICT);
  public static final S3ErrorCode AUTHORIZATION_HEADER_MALFORMED = new S3ErrorCode(
          Name.AUTHORIZATION_HEADER_MALFORMED,
          "The authorization header provided is invalid.",
          Response.Status.BAD_REQUEST);
  public static final S3ErrorCode AUTHINFO_CREATION_ERROR = new S3ErrorCode(
          Name.AUTHINFO_CREATION_ERROR,
          "Error creating s3 auth info",
          Response.Status.BAD_REQUEST);
  public static final S3ErrorCode ACCESS_DENIED_ERROR = new S3ErrorCode(
          Name.ACCESS_DENIED_ERROR,
          "User doesn't have the right to access this resource",
          Response.Status.FORBIDDEN);
  public static final S3ErrorCode INVALID_IDENTIFIER = new S3ErrorCode(
          Name.INVALID_IDENTIFIER,
          "Invalid S3 identifier",
          Response.Status.FORBIDDEN);
  public static final S3ErrorCode NOT_FOUND_CUSTOMIZED_SECRET_MANAGER = new S3ErrorCode(
          Name.NOT_FOUND_CUSTOMIZED_SECRET_MANAGER,
          "Not found implementation for S3SecretManager",
          Response.Status.INTERNAL_SERVER_ERROR);

  //
  // Customized error codes.
  //
  public static final S3ErrorCode INVALID_NESTED_BUCKET_NAME = new S3ErrorCode(
      Name.BUCKET_ALREADY_EXISTS,
      "The specified bucket is not a directory directly under a mount point",
      Response.Status.BAD_REQUEST);

  private final String mCode;
  private final String mDescription;
  private final Response.Status mStatus;

  /**
   * Constructs a new {@link S3ErrorCode}.
   *
   * @param code the error code defined in {@link Name}
   * @param description the error description
   * @param status the HTTP status code for the error
   */
  public S3ErrorCode(String code, String description, Response.Status status) {
    mCode = code;
    mDescription = description;
    mStatus = status;
  }

  /**
   * @return the error code
   */
  public String getCode() {
    return mCode;
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return mDescription;
  }

  /**
   * @return the HTTP status
   */
  public Response.Status getStatus() {
    return mStatus;
  }
}
