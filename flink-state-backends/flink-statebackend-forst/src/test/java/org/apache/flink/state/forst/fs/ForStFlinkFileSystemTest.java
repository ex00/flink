/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.state.forst.fs;

import org.apache.flink.core.fs.ByteBufferReadable;
import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.fs.local.LocalDataInputStream;
import org.apache.flink.core.fs.local.LocalFileSystem;
import org.apache.flink.util.concurrent.FutureUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link ForStFlinkFileSystem}. */
public class ForStFlinkFileSystemTest {

    @TempDir Path tempDir;

    @Test
    void testReadAndWriteWithByteBuffer() throws Exception {
        ForStFlinkFileSystem.setupLocalBasePath(tempDir.toString(), tempDir.toString());
        ForStFlinkFileSystem fileSystem =
                (ForStFlinkFileSystem) ForStFlinkFileSystem.get(new URI(tempDir.toString()));
        fileSystem.setupLocalBasePath(tempDir.toString(), tempDir.toString());
        testReadAndWriteWithByteBuffer(fileSystem);
    }

    @Test
    void testPositionedRead() throws Exception {
        ForStFlinkFileSystem.setupLocalBasePath(tempDir.toString(), tempDir.toString());
        ForStFlinkFileSystem fileSystem =
                new ForStFlinkFileSystem(
                        new ByteBufferReadableLocalFileSystem(),
                        tempDir.toString(),
                        tempDir.toString());
        testReadAndWriteWithByteBuffer(fileSystem);
    }

    private void testReadAndWriteWithByteBuffer(ForStFlinkFileSystem fileSystem) throws Exception {
        org.apache.flink.core.fs.Path testFilePath =
                new org.apache.flink.core.fs.Path(tempDir.toString() + "/temp-file");
        final int attempt = 200;

        // Test write with ByteBuffer
        ByteBufferWritableFSDataOutputStream outputStream = fileSystem.create(testFilePath);
        ByteBuffer writeBuffer = ByteBuffer.allocate(20);
        for (int i = 0; i < attempt; i++) {
            writeBuffer.clear();
            writeBuffer.position(2);
            writeBuffer.putLong(i);
            writeBuffer.putLong(i * 2);
            writeBuffer.flip();
            writeBuffer.position(2);
            outputStream.write(writeBuffer);
        }
        outputStream.flush();
        outputStream.close();

        // Test sequential read with ByteBuffer
        ByteBufferReadableFSDataInputStream inputStream = fileSystem.open(testFilePath);
        inputStream.seek(0);
        ByteBuffer readBuffer = ByteBuffer.allocate(20);
        for (int i = 0; i < attempt; i++) {
            readBuffer.clear();
            readBuffer.position(1);
            readBuffer.limit(17);
            int read = inputStream.readFully(readBuffer);
            assertThat(read).isEqualTo(16);
            assertThat(readBuffer.getLong(1)).isEqualTo(i);
            assertThat(readBuffer.getLong(9)).isEqualTo(i * 2);
        }
        inputStream.close();

        // Test random read with ByteBuffer concurrently
        ByteBufferReadableFSDataInputStream randomInputStream = fileSystem.open(testFilePath);
        List<CompletableFuture<Void>> futureList = new ArrayList<>();
        for (int index = 0; index < attempt; index++) {
            futureList.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    ByteBuffer randomReadBuffer = ByteBuffer.allocate(20);
                                    for (int i = 0; i < attempt; i += 2) {
                                        randomReadBuffer.clear();
                                        randomReadBuffer.position(1);
                                        randomReadBuffer.limit(17);
                                        int read =
                                                randomInputStream.readFully(
                                                        i * 16L, randomReadBuffer);
                                        assertThat(read).isEqualTo(16);
                                        assertThat(randomReadBuffer.getLong(1)).isEqualTo(i);
                                        assertThat(randomReadBuffer.getLong(9)).isEqualTo(i * 2L);
                                    }
                                } catch (Exception ex) {
                                    throw new CompletionException(ex);
                                }
                            }));
        }
        FutureUtils.waitForAll(futureList).get();
        inputStream.close();

        assertThat(fileSystem.exists(testFilePath)).isTrue();
        fileSystem.delete(testFilePath, true);
        assertThat(fileSystem.exists(testFilePath)).isFalse();
    }

    @Test
    void testReadExceedingFileSize() throws Exception {
        ForStFlinkFileSystem.setupLocalBasePath(tempDir.toString(), tempDir.toString());
        ForStFlinkFileSystem fileSystem =
                new ForStFlinkFileSystem(
                        new ByteBufferReadableLocalFileSystem(),
                        tempDir.toString(),
                        tempDir.toString());

        org.apache.flink.core.fs.Path testFilePath =
                new org.apache.flink.core.fs.Path(tempDir.toString() + "/temp-file");
        try (ByteBufferWritableFSDataOutputStream outputStream = fileSystem.create(testFilePath)) {
            outputStream.write(1);
        }

        try (ByteBufferReadableFSDataInputStream inputStream = fileSystem.open(testFilePath)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(2);
            inputStream.readFully(0, byteBuffer);
            inputStream.readFully(byteBuffer);
        }
    }

    @Test
    void testMiscFileInLocal() throws IOException {
        org.apache.flink.core.fs.Path remotePath =
                new org.apache.flink.core.fs.Path(tempDir.toString() + "/remote");
        org.apache.flink.core.fs.Path localPath =
                new org.apache.flink.core.fs.Path(tempDir.toString() + "/local");
        ForStFlinkFileSystem.setupLocalBasePath(remotePath.toString(), localPath.toString());
        ForStFlinkFileSystem fileSystem =
                new ForStFlinkFileSystem(
                        new ByteBufferReadableLocalFileSystem(),
                        remotePath.toString(),
                        localPath.toString());
        fileSystem.mkdirs(remotePath);
        fileSystem.mkdirs(localPath);

        ByteBufferWritableFSDataOutputStream os =
                fileSystem.create(new org.apache.flink.core.fs.Path(remotePath, "CURRENT"));
        os.write(233);
        os.sync();
        os.close();
        assertThat(fileSystem.exists(new org.apache.flink.core.fs.Path(localPath, "CURRENT")))
                .isTrue();
        ByteBufferReadableFSDataInputStream is =
                fileSystem.open(new org.apache.flink.core.fs.Path(remotePath, "CURRENT"));
        assertThat(is.read()).isEqualTo(233);
        is.close();
    }

    private static class ByteBufferReadableLocalFileSystem extends LocalFileSystem {

        @Override
        public FSDataInputStream open(org.apache.flink.core.fs.Path path) throws IOException {
            final File file = pathToFile(path);
            return new ByteBufferReadableLocalDataInputStream(file);
        }
    }

    private static class ByteBufferReadableLocalDataInputStream extends LocalDataInputStream
            implements ByteBufferReadable {

        private final long totalFileSize;

        public ByteBufferReadableLocalDataInputStream(File file) throws IOException {
            super(file);
            this.totalFileSize = file.length();
        }

        @Override
        public synchronized int read(ByteBuffer byteBuffer) throws IOException {
            assertThat((long) byteBuffer.remaining()).isLessThanOrEqualTo(totalFileSize);
            byte[] tmp = new byte[byteBuffer.remaining()];
            read(tmp, 0, tmp.length);
            byteBuffer.put(tmp);
            return tmp.length;
        }

        @Override
        public synchronized int read(long position, ByteBuffer byteBuffer) throws IOException {
            assertThat(position + byteBuffer.remaining()).isLessThanOrEqualTo(totalFileSize);
            byte[] tmp = new byte[byteBuffer.remaining()];
            long currPosition = getPos();
            seek(position);
            read(tmp, 0, tmp.length);
            seek(currPosition);
            byteBuffer.put(tmp);
            return tmp.length;
        }
    }
}
