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
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.client.api.response.BrokerInfo;
import io.zeebe.client.api.response.PartitionInfo;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

// FIXME: rewrite tests now that leader election is not controllable
public class BrokerLeaderChangeTest {
  public static final String NULL_VARIABLES = null;
  public static final String JOB_TYPE = "testTask";
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public Timeout testTimeout = Timeout.seconds(120);
  public ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3);
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBecomeFollowerAfterRestartLeaderChange() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final int oldLeader = clusteringRule.getLeaderForPartition(partition).getNodeId();

    clusteringRule.stopBroker(oldLeader);

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeader);

    // when
    clusteringRule.restartBroker(oldLeader);

    // then

    final Stream<PartitionInfo> partitionInfo =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition));

    assertThat(partitionInfo.allMatch(p -> !p.isLeader())).isTrue();
  }

  @Test
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);

    final long jobKey = clientRule.createSingleJob(JOB_TYPE);

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId());
    final JobCompleter jobCompleter = new JobCompleter(jobKey);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  class JobCompleter {
    private final JobWorker jobWorker;
    private final CountDownLatch latch = new CountDownLatch(1);

    JobCompleter(final long jobKey) {

      jobWorker =
          clientRule
              .getClient()
              .newWorker()
              .jobType(JOB_TYPE)
              .handler(
                  (client, job) -> {
                    if (job.getKey() == jobKey) {
                      client.newCompleteCommand(job.getKey()).send();
                      latch.countDown();
                    }
                  })
              .open();
    }

    void waitForJobCompletion() {
      try {
        latch.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      assertJobCompleted();
    }

    void close() {
      if (!jobWorker.isClosed()) {
        jobWorker.close();
      }
    }
  }
}
