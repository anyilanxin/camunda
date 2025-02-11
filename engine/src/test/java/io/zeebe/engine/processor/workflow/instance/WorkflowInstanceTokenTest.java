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
package io.zeebe.engine.processor.workflow.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowInstanceTokenTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String processId;

  @Before
  public void setUp() {
    processId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCompleteInstanceAfterEndEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end").done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterEventWithoutOutgoingSequenceFlows() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent("start").done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "start");
  }

  @Test
  public void shouldCompleteInstanceAfterActivityWithoutOutgoingSequenceFlows() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "task");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelSplit() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelJoin() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
                .parallelGateway("join")
                .endEvent("end")
                .moveToNode("fork")
                .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
                .connectTo("join")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterMessageIntermediateCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("key")))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'key':'123'}")
            .create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();
    ENGINE.message().withName("msg").withCorrelationKey("123").publish();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterTimerIntermediateCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent("catch", e -> e.timerWithDuration("PT0.1S"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterSubProcessEnded() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .subProcess(
                    "sub",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
                            .endEvent("end-sub"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterEventBasedGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .eventBasedGateway("gateway")
                .intermediateCatchEvent(
                    "catch-1", e -> e.message(m -> m.name("msg-1").zeebeCorrelationKey("key")))
                .endEvent("end-2")
                .moveToNode("gateway")
                .intermediateCatchEvent(
                    "catch-2", e -> e.message(m -> m.name("msg-2").zeebeCorrelationKey("key")))
                .endEvent("end-3")
                .done())
        .deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'key':'123'}")
            .create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();
    ENGINE.message().withName("msg-1").withCorrelationKey("123").publish();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterInterruptingBoundaryEventTriggered() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToActivity("task")
                .boundaryEvent("timeout", b -> b.cancelActivity(true).timerWithDuration("PT0.1S"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    RecordingExporter.jobRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterNonInterruptingBoundaryEventTriggered() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
                .endEvent("end-1")
                .moveToActivity("task-1")
                .boundaryEvent("timeout", b -> b.cancelActivity(false).timerWithCycle("R1/PT0.1S"))
                .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    RecordingExporter.jobRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-1");
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .intermediateCatchEvent(
                    "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("key")))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("key", "123")))
        .update();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incident.getKey()).resolve();
    ENGINE.message().withName("msg").withCorrelationKey("123").publish();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnActivity() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
                .endEvent("end-1")
                .moveToLastGateway()
                .serviceTask("task-2", t -> t.zeebeTaskType("task-2").zeebeOutput("result", "r"))
                .endEvent("end-2")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-2").complete();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task-1").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("result", "123")))
        .update();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnExclusiveGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .parallelGateway()
                .serviceTask("task", t -> t.zeebeTaskType("task"))
                .endEvent("end-1")
                .moveToLastGateway()
                .exclusiveGateway("gateway")
                .defaultFlow()
                .endEvent("end-2")
                .moveToNode("gateway")
                .sequenceFlowId("to-end-3")
                .condition("x < 21")
                .endEvent("end-3")
                .done())
        .deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.job().ofInstance(workflowInstanceKey).withType("task").complete();

    ENGINE
        .variables()
        .ofScope(incident.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("x", 123)))
        .update();

    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  private void assertThatWorkflowInstanceCompletedAfter(
      long workflowInstanceKey, String elementId) {
    final Record<WorkflowInstanceRecordValue> lastEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(processId)
            .getFirst();

    assertThat(completedEvent.getPosition()).isGreaterThan(lastEvent.getPosition());
  }
}
