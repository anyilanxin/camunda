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
package io.zeebe.distributedlog.restore.snapshot.impl;

import io.zeebe.distributedlog.restore.RestoreServer.SnapshotRequestHandler;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreRequest;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreResponse;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotChunkUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;

public class DefaultSnapshotRequestHandler implements SnapshotRequestHandler {
  private final SnapshotController snapshotController;

  public DefaultSnapshotRequestHandler(SnapshotController snapshotStorage) {
    this.snapshotController = snapshotStorage;
  }

  @Override
  public SnapshotRestoreResponse onSnapshotRequest(SnapshotRestoreRequest request, Logger logger) {
    final File snapshotDirectory =
        snapshotController.getSnapshotDirectoryFor(request.getSnapshotId());
    SnapshotRestoreResponse response = new InvalidSnapshotRestoreResponse();

    logger.debug("Received on demand snapshot request {}", request);
    if (snapshotDirectory.exists()) {
      final File[] files = snapshotDirectory.listFiles();
      if (files != null && files.length > 0) {
        Arrays.sort(files);

        if (request.getChunkIdx() < files.length) {
          final File chunkFile = files[request.getChunkIdx()];
          try {
            final SnapshotChunk snapshotChunk =
                SnapshotChunkUtil.createSnapshotChunkFromFile(
                    chunkFile, request.getSnapshotId(), files.length);
            response = new SuccessSnapshotRestoreResponse(snapshotChunk);
          } catch (IOException e) {
            logger.warn(
                "Unexpected error when reading snapshot chunk file {} ({}) at index {}.",
                chunkFile.toString(),
                request.getSnapshotId(),
                request.getChunkIdx(),
                e);
          }
        }
      } else {
        logger.debug(
            "No snapshot files available ({}) but directory {} is present",
            files,
            snapshotDirectory);
      }
    }

    logger.debug("Responding on demand snapshot request with {}", response);
    return response;
  }
}
