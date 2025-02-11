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
package io.zeebe.broker.system.configuration;

import io.zeebe.transport.SocketAddress;

public class SocketBindingCfg {
  public static final String DEFAULT_SEND_BUFFER_SIZE = "16M";

  protected String host;
  protected int port;
  protected String sendBufferSize;

  private SocketBindingCfg(int defaultPort) {
    this.port = defaultPort;
  }

  public SocketAddress toSocketAddress() {
    return new SocketAddress(host, port);
  }

  public void applyDefaults(NetworkCfg networkCfg) {
    if (host == null) {
      host = networkCfg.getHost();
    }

    if (sendBufferSize == null) {
      sendBufferSize = DEFAULT_SEND_BUFFER_SIZE;
    }

    port += networkCfg.getPortOffset() * 10;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getSendBufferSize() {
    return sendBufferSize;
  }

  public void setSendBufferSize(String sendBufferSize) {
    this.sendBufferSize = sendBufferSize;
  }

  @Override
  public String toString() {
    return "SocketBindingCfg{"
        + "host='"
        + host
        + '\''
        + ", port="
        + port
        + ", sendBufferSize='"
        + sendBufferSize
        + '\''
        + '}';
  }

  public static class CommandApiCfg extends SocketBindingCfg {
    public CommandApiCfg() {
      super(NetworkCfg.DEFAULT_COMMAND_API_PORT);
    }
  }

  public static class InternalApiCfg extends SocketBindingCfg {
    public InternalApiCfg() {
      super(NetworkCfg.DEFAULT_INTERNAL_API_PORT);
    }
  }

  public static class MonitoringApiCfg extends SocketBindingCfg {
    public MonitoringApiCfg() {
      super(NetworkCfg.DEFAULT_MONITORING_API_PORT);
    }
  }
}
