/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import io.zeebe.engine.processor.CommandProcessor.CommandControl;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public class CommandProcessorImpl<T extends UnifiedRecordValue>
    implements TypedRecordProcessor<T>, CommandControl<T> {

  private final CommandProcessor<T> wrappedProcessor;

  private KeyGenerator keyGenerator;

  private boolean isAccepted;
  private long entityKey;

  private Intent newState;
  private T updatedValue;

  private RejectionType rejectionType;
  private String rejectionReason;

  public CommandProcessorImpl(final CommandProcessor<T> commandProcessor) {
    this.wrappedProcessor = commandProcessor;
  }

  @Override
  public void onOpen(ReadonlyProcessingContext context) {
    this.keyGenerator = context.getZeebeState().getKeyGenerator();
  }

  @Override
  public void processRecord(
      final TypedRecord<T> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    entityKey = command.getKey();
    wrappedProcessor.onCommand(command, this, streamWriter);

    final boolean respond = command.hasRequestMetadata();

    if (isAccepted) {
      streamWriter.appendFollowUpEvent(entityKey, newState, updatedValue);
      if (respond) {
        responseWriter.writeEventOnCommand(entityKey, newState, updatedValue, command);
      }
    } else {
      streamWriter.appendRejection(command, rejectionType, rejectionReason);
      if (respond) {
        responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
      }
    }
  }

  @Override
  public long accept(final Intent newState, final T updatedValue) {
    if (entityKey < 0) {
      entityKey = keyGenerator.nextKey();
    }

    isAccepted = true;
    this.newState = newState;
    this.updatedValue = updatedValue;
    return entityKey;
  }

  @Override
  public void reject(final RejectionType type, final String reason) {
    isAccepted = false;
    this.rejectionType = type;
    this.rejectionReason = reason;
  }
}
