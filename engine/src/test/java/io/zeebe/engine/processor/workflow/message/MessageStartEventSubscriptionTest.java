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
package io.zeebe.engine.processor.workflow.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class MessageStartEventSubscriptionTest {
  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  private static final String MESSAGE_NAME2 = "startMessage2";
  private static final String EVENT_ID2 = "startEventId2";

  @Rule public EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldOpenMessageSubscriptionOnDeployment() {

    // when
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    final Record<MessageStartEventSubscriptionRecordValue> subscription =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.OPENED)
            .getFirst();

    // then
    assertThat(subscription.getValue().getStartEventId()).isEqualTo(EVENT_ID1);
    assertThat(subscription.getValue().getMessageName()).isEqualTo(MESSAGE_NAME1);
  }

  @Test
  public void shouldOpenSubscriptionsForAllMessageStartEvents() {

    // when
    engine.deployment().withXmlResource(createWorkflowWithTwoMessageStartEvent()).deploy();

    final List<Record<MessageStartEventSubscriptionRecordValue>> subscriptions =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.OPENED)
            .limit(2)
            .asList();

    // then
    assertThat(subscriptions.size()).isEqualTo(2);

    assertThat(subscriptions)
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(s -> tuple(s.getMessageName(), s.getStartEventId()))
        .containsExactlyInAnyOrder(
            tuple(MESSAGE_NAME1, EVENT_ID1), tuple(MESSAGE_NAME2, EVENT_ID2));
  }

  @Test
  public void shouldCloseSubscriptionForOldVersions() {
    // given
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    // when
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();
    // then

    final List<Record<MessageStartEventSubscriptionRecordValue>> subscriptions =
        RecordingExporter.messageStartEventSubscriptionRecords().limit(6).asList();

    final List<Intent> intents =
        subscriptions.stream().map(Record::getIntent).collect(Collectors.toList());

    assertThat(intents)
        .containsExactly(
            MessageStartEventSubscriptionIntent.OPEN,
            MessageStartEventSubscriptionIntent.OPENED,
            MessageStartEventSubscriptionIntent.CLOSE, // close old version
            MessageStartEventSubscriptionIntent.OPEN, // open new
            MessageStartEventSubscriptionIntent.CLOSED,
            MessageStartEventSubscriptionIntent.OPENED);

    final long closingWorkflowKey = subscriptions.get(2).getValue().getWorkflowKey();
    assertThat(closingWorkflowKey).isEqualTo(subscriptions.get(0).getValue().getWorkflowKey());
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }

  private static BpmnModelInstance createWorkflowWithTwoMessageStartEvent() {
    final ProcessBuilder process = Bpmn.createExecutableProcess("processId");
    process.startEvent(EVENT_ID1).message(m -> m.name(MESSAGE_NAME1).id("startmsgId1")).endEvent();
    process.startEvent(EVENT_ID2).message(m -> m.name(MESSAGE_NAME2).id("startmsgId2")).endEvent();

    return process.done();
  }
}
