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
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.util.stream.StreamWrapper;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class ExporterRecordStream<
        T extends RecordValue, S extends ExporterRecordStream<T, S>>
    extends StreamWrapper<Record<T>, S> {

  public ExporterRecordStream(final Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  public S valueFilter(final Predicate<T> predicate) {
    return filter(r -> predicate.test(r.getValue()));
  }

  public S onlyCommands() {
    return filter(m -> m.getRecordType() == RecordType.COMMAND);
  }

  public S onlyCommandRejections() {
    return filter(m -> m.getRecordType() == RecordType.COMMAND_REJECTION);
  }

  public S onlyEvents() {
    return filter(m -> m.getRecordType() == RecordType.EVENT);
  }

  public S withPosition(final long position) {
    return filter(r -> r.getPosition() == position);
  }

  public S withSourceRecordPosition(final long sourceRecordPosition) {
    return filter(r -> r.getSourceRecordPosition() == sourceRecordPosition);
  }

  public S withRecordKey(final long key) {
    return filter(r -> r.getKey() == key);
  }

  public S withTimestamp(final long timestamp) {
    return filter(r -> r.getTimestamp() == timestamp);
  }

  public S withIntent(final Intent intent) {
    return filter(m -> m.getIntent() == intent);
  }

  public S withPartitionId(final int partitionId) {
    return filter(m -> m.getPartitionId() == partitionId);
  }

  public S withRecordType(final RecordType recordType) {
    return filter(m -> m.getRecordType() == recordType);
  }

  public S withRejectionType(final RejectionType rejectionType) {
    return filter(m -> m.getRejectionType() == rejectionType);
  }

  public S withRejectionReason(final String rejectionReason) {
    return filter(m -> rejectionReason.equals(m.getRejectionReason()));
  }

  public S withValueType(final ValueType valueType) {
    return filter(m -> m.getValueType() == valueType);
  }
}
