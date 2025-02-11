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
package io.zeebe.engine.processor.workflow.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordStream;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UpdateVariableDocumentTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldProduceCorrectSequenceOfEvents() {
    // given
    final String processId = "process";
    final String taskId = "task";
    final String type = UUID.randomUUID().toString();
    final BpmnModelInstance process = newWorkflow(processId, taskId, type);
    final Map<String, Object> document = Maps.of(entry("x", 2), entry("foo", "bar"));

    // when
    ENGINE_RULE.deployment().withXmlResource(process).deploy();
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{'x': 1}")
            .create();
    final Record<WorkflowInstanceRecordValue> activatedEvent = waitForActivityActivatedEvent();
    ENGINE_RULE
        .variables()
        .ofScope(activatedEvent.getKey())
        .withDocument(document)
        .withUpdateSemantic(VariableDocumentUpdateSemantic.PROPAGATE)
        .update();
    ENGINE_RULE.job().ofInstance(workflowInstanceKey).withType(type).complete();

    // then
    final long completedPosition =
        getWorkflowInstanceCompletedPosition(processId, workflowInstanceKey);
    final Supplier<RecordStream> recordsSupplier =
        () -> RecordingExporter.records().between(activatedEvent.getPosition(), completedPosition);

    assertVariableRecordsProduced(workflowInstanceKey, recordsSupplier);
    assertVariableDocumentEventProduced(document, activatedEvent, recordsSupplier);
  }

  private void assertVariableDocumentEventProduced(
      Map<String, Object> document,
      Record<WorkflowInstanceRecordValue> activatedEvent,
      Supplier<RecordStream> records) {
    assertThat(
            records
                .get()
                .variableDocumentRecords()
                .withIntent(VariableDocumentIntent.UPDATED)
                .withScopeKey(activatedEvent.getKey())
                .withUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
                .withVariables(document)
                .getFirst())
        .isNotNull();
  }

  private void assertVariableRecordsProduced(
      long workflowInstanceKey, Supplier<RecordStream> records) {
    assertThat(records.get().variableRecords().withWorkflowInstanceKey(workflowInstanceKey))
        .hasSize(2)
        .extracting(
            r ->
                tuple(
                    r.getIntent(),
                    r.getValue().getScopeKey(),
                    r.getValue().getName(),
                    r.getValue().getValue()))
        .contains(
            tuple(VariableIntent.CREATED, workflowInstanceKey, "foo", "\"bar\""),
            tuple(VariableIntent.UPDATED, workflowInstanceKey, "x", "2"));
  }

  private BpmnModelInstance newWorkflow(String processId, String taskId, String type) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask(taskId, b -> b.zeebeTaskType(type))
        .endEvent()
        .done();
  }

  private long getWorkflowInstanceCompletedPosition(String processId, long workflowInstanceKey) {
    return RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId(processId)
        .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .getFirst()
        .getPosition();
  }

  private Record<WorkflowInstanceRecordValue> waitForActivityActivatedEvent() {
    return RecordingExporter.workflowInstanceRecords()
        .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .getFirst();
  }
}
