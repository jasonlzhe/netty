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
package io.netty.handler.codec.replay;

import static org.junit.Assert.*;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferIndexFinder;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Test;

public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        DecoderEmbedder<ChannelBuffer> e = new DecoderEmbedder<ChannelBuffer>(
                new LineDecoder());

        // Ordinary input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'B' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'C' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { '\n' }));
        assertEquals(ChannelBuffers.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), e.poll());

        // Truncated input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.finish();
        assertNull(e.poll());
    }

    private static final class LineDecoder extends ReplayingDecoder<VoidEnum> {

        LineDecoder() {
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel,
                ChannelBuffer buffer, VoidEnum state) throws Exception {
            ChannelBuffer msg = buffer.readBytes(
                    buffer.bytesBefore(ChannelBufferIndexFinder.LF));
            buffer.skipBytes(1);
            return msg;
        }
    }
}
