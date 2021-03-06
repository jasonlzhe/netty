/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.ServerChannelFactory;
import io.netty.channel.socket.nio.SelectorUtil;
import io.netty.channel.socket.nio.WorkerPool;
import io.netty.util.ExternalResourceReleasable;

import java.util.concurrent.Executor;

/**
 * A {@link io.netty.channel.socket.ServerSocketChannelFactory} which creates a server-side NIO-based
 * {@link io.netty.channel.socket.ServerSocketChannel}.  It utilizes the non-blocking I/O mode which
 * was introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link SctpServerSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss threads</h4>
 * <p>
 * Each bound {@link io.netty.channel.socket.ServerSocketChannel} has its own boss thread.
 * For example, if you opened two server ports such as 80 and 443, you will
 * have two boss threads.  A boss thread accepts incoming connections until
 * the port is unbound.  Once a connection is accepted successfully, the boss
 * thread passes the accepted {@link io.netty.channel.Channel} to one of the worker
 * threads that the {@link SctpServerSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link SctpServerSocketChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link io.netty.channel.Channel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link java.util.concurrent.Executor}s which were specified
 * when a {@link SctpServerSocketChannelFactory} was created.  Boss threads are
 * acquired from the {@code bossExecutor}, and worker threads are acquired from
 * the {@code workerExecutor}.  Therefore, you should make sure the specified
 * {@link java.util.concurrent.Executor}s are able to lend the sufficient number of threads.
 * It is the best bet to specify {@linkplain java.util.concurrent.Executors#newCachedThreadPool() a cached thread pool}.
 * <p>
 * Both boss and worker threads are acquired lazily, and then released when
 * there's nothing left to process.  All the related resources such as
 * {@link java.nio.channels.Selector} are also released when the boss and worker threads are
 * released.  Therefore, to shut down a service gracefully, you should do the
 * following:
 *
 * <ol>
 * <li>unbind all channels created by the factory,
 * <li>close all child channels accepted by the unbound channels, and
 *     (these two steps so far is usually done using {@link io.netty.channel.group.ChannelGroup#close()})</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link java.util.concurrent.RejectedExecutionException}
 * and the related resources might not be released properly.
 * @apiviz.landmark
 */
public class SctpServerSocketChannelFactory implements ServerChannelFactory {

    private final ChannelSink sink;
    private final WorkerPool<SctpWorker> workerPool;

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #SctpServerSocketChannelFactory(java.util.concurrent.Executor, java.util.concurrent.Executor, int)} with 2 *
     * the number of available processors in the machine.  The number of
     * available processors is obtained by {@link Runtime#availableProcessors()}.
     *
     * @param workerExecutor
     *        the {@link java.util.concurrent.Executor} which will execute the I/O worker threads
     */
    public SctpServerSocketChannelFactory(Executor workerExecutor) {
        this(workerExecutor, SelectorUtil.DEFAULT_IO_THREADS);
    }

    /**
     * Creates a new instance.
     *
     * @param workerExecutor
     *        the {@link java.util.concurrent.Executor} which will execute the I/O worker threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public SctpServerSocketChannelFactory(Executor workerExecutor,
            int workerCount) {
        this(new SctpWorkerPool(workerExecutor, workerCount, true));
    }

    public SctpServerSocketChannelFactory(WorkerPool<SctpWorker> workerPool) {
        if (workerPool == null) {
            throw new NullPointerException("workerPool");
        }
        this.workerPool = workerPool;
        sink = new SctpServerPipelineSink(workerPool);
    }
    
    @Override
    public SctpServerChannel newChannel(ChannelPipeline pipeline) {
        return new SctpServerChannelImpl(this, pipeline, sink, workerPool.nextWorker());
    }


    @Override
    public void releaseExternalResources() {
        if (workerPool instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) workerPool).releaseExternalResources();
        }
        
    }
}
