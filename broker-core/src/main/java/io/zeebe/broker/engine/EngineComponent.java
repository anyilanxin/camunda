/*
 * Zeebe Broker Core
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
package io.zeebe.broker.engine;

import static io.zeebe.broker.engine.EngineServiceNames.ENGINE_SERVICE_NAME;
import static io.zeebe.broker.system.SystemServiceNames.LEADER_MANAGEMENT_REQUEST_HANDLER;

import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;

public class EngineComponent implements Component {

  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();
    final BrokerCfg brokerConfiguration = context.getBrokerConfiguration();

    final EngineService streamProcessorService =
        new EngineService(serviceContainer, brokerConfiguration);
    serviceContainer
        .createService(ENGINE_SERVICE_NAME, streamProcessorService)
        .dependency(
            TransportServiceNames.serverTransport(TransportServiceNames.COMMAND_API_SERVER_NAME),
            streamProcessorService.getCommandApiTransportInjector())
        .dependency(
            ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE,
            streamProcessorService.getTopologyManagerInjector())
        .dependency(
            ClusterBaseLayerServiceNames.ATOMIX_SERVICE, streamProcessorService.getAtomixInjector())
        .dependency(
            LEADER_MANAGEMENT_REQUEST_HANDLER,
            streamProcessorService.getLeaderManagementRequestInjector())
        .groupReference(
            ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME,
            streamProcessorService.getPartitionsGroupReference())
        .install();

    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
        new SubscriptionApiCommandMessageHandlerService();
    serviceContainer
        .createService(
            EngineServiceNames.SUBSCRIPTION_API_MESSAGE_HANDLER_SERVICE_NAME, messageHandlerService)
        .dependency(
            ClusterBaseLayerServiceNames.ATOMIX_SERVICE, messageHandlerService.getAtomixInjector())
        .groupReference(
            ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME,
            messageHandlerService.getLeaderParitionsGroupReference())
        .install();
  }
}
