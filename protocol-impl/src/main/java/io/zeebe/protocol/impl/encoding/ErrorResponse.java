/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.encoding;

import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.ErrorResponseEncoder;
import io.zeebe.protocol.record.MessageHeaderDecoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ErrorResponse implements BufferWriter, BufferReader {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
  private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

  private ErrorCode errorCode;
  private final DirectBuffer errorData = new UnsafeBuffer();

  public ErrorResponse() {
    reset();
  }

  public ErrorResponse reset() {
    errorCode = ErrorCode.NULL_VAL;
    errorData.wrap(0, 0);

    return this;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public ErrorResponse setErrorCode(ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public DirectBuffer getErrorData() {
    return errorData;
  }

  public ErrorResponse setErrorData(DirectBuffer errorData) {
    this.errorData.wrap(errorData, 0, errorData.capacity());
    return this;
  }

  public boolean tryWrap(DirectBuffer buffer) {
    return tryWrap(buffer, 0, buffer.capacity());
  }

  public boolean tryWrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    return headerDecoder.schemaId() == bodyDecoder.sbeSchemaId()
        && headerDecoder.templateId() == bodyDecoder.sbeTemplateId();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    errorCode = bodyDecoder.errorCode();

    offset += bodyDecoder.sbeBlockLength();

    final int errorDataLength = bodyDecoder.errorDataLength();
    offset += ErrorResponseDecoder.errorDataHeaderLength();

    errorData.wrap(buffer, offset, errorDataLength);
    offset += errorDataLength;

    bodyDecoder.limit(offset);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ErrorResponseEncoder.BLOCK_LENGTH
        + ErrorResponseEncoder.errorDataHeaderLength()
        + errorData.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder
        .wrap(buffer, offset)
        .errorCode(errorCode)
        .putErrorData(errorData, 0, errorData.capacity());
  }

  public byte[] toBytes() {
    final byte[] bytes = new byte[getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);
    return bytes;
  }
}
