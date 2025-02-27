/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DefaultHttp2LocalFlowController}.
 */
public class DefaultHttp2LocalFlowControllerTest {
    private static final int STREAM_ID = 1;

    private DefaultHttp2LocalFlowController controller;

    @Mock
    private ByteBuf buffer;

    @Mock
    private Http2FrameWriter frameWriter;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private ChannelPromise promise;

    private DefaultHttp2Connection connection;

    private static float updateRatio = 0.5f;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.flush()).thenThrow(new AssertionFailedError("forbidden"));

        connection = new DefaultHttp2Connection(false);
        controller = new DefaultHttp2LocalFlowController(connection, frameWriter, updateRatio);
        connection.local().flowController(controller);

        connection.local().createStream(STREAM_ID, false);
        controller.channelHandlerContext(ctx);
    }

    @Test
    public void dataFrameShouldBeAccepted() throws Http2Exception {
        receiveFlowControlledFrame(STREAM_ID, 10, 0, false);
        verifyWindowUpdateNotSent();
    }

    @Test
    public void windowUpdateShouldSendOnceBytesReturned() throws Http2Exception {
        int dataSize = (int) (DEFAULT_WINDOW_SIZE * updateRatio) + 1;
        receiveFlowControlledFrame(STREAM_ID, dataSize, 0, false);

        // Return only a few bytes and verify that the WINDOW_UPDATE hasn't been sent.
        assertFalse(consumeBytes(STREAM_ID, 10));
        verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);

        // Return the rest and verify the WINDOW_UPDATE is sent.
        assertTrue(consumeBytes(STREAM_ID, dataSize - 10));
        verifyWindowUpdateSent(STREAM_ID, dataSize);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, dataSize);
    }

    @Test(expected = Http2Exception.class)
    public void connectionFlowControlExceededShouldThrow() throws Http2Exception {
        // Window exceeded because of the padding.
        receiveFlowControlledFrame(STREAM_ID, DEFAULT_WINDOW_SIZE, 1, true);
    }

    @Test
    public void windowUpdateShouldNotBeSentAfterEndOfStream() throws Http2Exception {
        int dataSize = (int) (DEFAULT_WINDOW_SIZE * updateRatio) + 1;

        // Set end-of-stream on the frame, so no window update will be sent for the stream.
        receiveFlowControlledFrame(STREAM_ID, dataSize, 0, true);
        verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);
        verifyWindowUpdateNotSent(STREAM_ID);

        assertTrue(consumeBytes(STREAM_ID, dataSize));
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, dataSize);
        verifyWindowUpdateNotSent(STREAM_ID);
    }

    @Test
    public void halfWindowRemainingShouldUpdateAllWindows() throws Http2Exception {
        int dataSize = (int) (DEFAULT_WINDOW_SIZE * updateRatio) + 1;
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        int windowDelta = getWindowDelta(initialWindowSize, initialWindowSize, dataSize);

        // Don't set end-of-stream so we'll get a window update for the stream as well.
        receiveFlowControlledFrame(STREAM_ID, dataSize, 0, false);
        assertTrue(consumeBytes(STREAM_ID, dataSize));
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, windowDelta);
        verifyWindowUpdateSent(STREAM_ID, windowDelta);
    }

    @Test
    public void initialWindowUpdateShouldAllowMoreFrames() throws Http2Exception {
        // Send a frame that takes up the entire window.
        int initialWindowSize = DEFAULT_WINDOW_SIZE;
        receiveFlowControlledFrame(STREAM_ID, initialWindowSize, 0, false);
        assertEquals(0, window(STREAM_ID));
        assertEquals(0, window(CONNECTION_STREAM_ID));
        consumeBytes(STREAM_ID, initialWindowSize);
        assertEquals(initialWindowSize, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

        // Update the initial window size to allow another frame.
        int newInitialWindowSize = 2 * initialWindowSize;
        controller.initialWindowSize(newInitialWindowSize);
        assertEquals(newInitialWindowSize, window(STREAM_ID));
        assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

        // Clear any previous calls to the writer.
        reset(frameWriter);

        // Send the next frame and verify that the expected window updates were sent.
        receiveFlowControlledFrame(STREAM_ID, initialWindowSize, 0, false);
        assertTrue(consumeBytes(STREAM_ID, initialWindowSize));
        int delta = newInitialWindowSize - initialWindowSize;
        verifyWindowUpdateSent(STREAM_ID, delta);
        verifyWindowUpdateSent(CONNECTION_STREAM_ID, delta);
    }

    @Test
    public void connectionWindowShouldAdjustWithMultipleStreams() throws Http2Exception {
        int newStreamId = 3;
        connection.local().createStream(newStreamId, false);

        try {
            assertEquals(DEFAULT_WINDOW_SIZE, window(STREAM_ID));
            assertEquals(DEFAULT_WINDOW_SIZE, window(CONNECTION_STREAM_ID));

            // Test that both stream and connection window are updated (or not updated) together
            int data1 = (int) (DEFAULT_WINDOW_SIZE * updateRatio) + 1;
            receiveFlowControlledFrame(STREAM_ID, data1, 0, false);
            verifyWindowUpdateNotSent(STREAM_ID);
            verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(STREAM_ID));
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(CONNECTION_STREAM_ID));
            assertTrue(consumeBytes(STREAM_ID, data1));
            verifyWindowUpdateSent(STREAM_ID, data1);
            verifyWindowUpdateSent(CONNECTION_STREAM_ID, data1);

            reset(frameWriter);

            // Create a scenario where data is depleted from multiple streams, but not enough data
            // to generate a window update on those streams. The amount will be enough to generate
            // a window update for the connection stream.
            --data1;
            int data2 = data1 >> 1;
            receiveFlowControlledFrame(STREAM_ID, data1, 0, false);
            receiveFlowControlledFrame(newStreamId, data1, 0, false);
            verifyWindowUpdateNotSent(STREAM_ID);
            verifyWindowUpdateNotSent(newStreamId);
            verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(STREAM_ID));
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(newStreamId));
            assertEquals(DEFAULT_WINDOW_SIZE - (data1 << 1), window(CONNECTION_STREAM_ID));
            assertFalse(consumeBytes(STREAM_ID, data1));
            assertTrue(consumeBytes(newStreamId, data2));
            verifyWindowUpdateNotSent(STREAM_ID);
            verifyWindowUpdateNotSent(newStreamId);
            verifyWindowUpdateSent(CONNECTION_STREAM_ID, data1 + data2);
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(STREAM_ID));
            assertEquals(DEFAULT_WINDOW_SIZE - data1, window(newStreamId));
            assertEquals(DEFAULT_WINDOW_SIZE - (data1 - data2), window(CONNECTION_STREAM_ID));
        } finally {
            connection.stream(newStreamId).close();
        }
    }

    @Test
    public void closeShouldConsumeBytes() throws Http2Exception {
        receiveFlowControlledFrame(STREAM_ID, 10, 0, false);
        assertEquals(10, controller.unconsumedBytes(connection.connectionStream()));
        stream(STREAM_ID).close();
        assertEquals(0, controller.unconsumedBytes(connection.connectionStream()));
    }

    @Test
    public void dataReceivedForClosedStreamShouldImmediatelyConsumeBytes() throws Http2Exception {
        Http2Stream stream = stream(STREAM_ID);
        stream.close();
        receiveFlowControlledFrame(stream, 10, 0, false);
        assertEquals(0, controller.unconsumedBytes(connection.connectionStream()));
    }

    @Test
    public void dataReceivedForNullStreamShouldImmediatelyConsumeBytes() throws Http2Exception {
        receiveFlowControlledFrame(null, 10, 0, false);
        assertEquals(0, controller.unconsumedBytes(connection.connectionStream()));
    }

    @Test
    public void consumeBytesForNullStreamShouldIgnore() throws Http2Exception {
        controller.consumeBytes(null, 10);
        assertEquals(0, controller.unconsumedBytes(connection.connectionStream()));
    }

    @Test
    public void globalRatioShouldImpactStreams() throws Http2Exception {
        float ratio = 0.6f;
        controller.windowUpdateRatio(ratio);
        testRatio(ratio, DEFAULT_WINDOW_SIZE << 1, 3, false);
    }

    @Test
    public void streamlRatioShouldImpactStreams() throws Http2Exception {
        float ratio = 0.6f;
        testRatio(ratio, DEFAULT_WINDOW_SIZE << 1, 3, true);
    }

    @Test
    public void consumeBytesForZeroNumBytesShouldIgnore() throws Http2Exception {
        assertFalse(controller.consumeBytes(connection.stream(STREAM_ID), 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void consumeBytesForNegativeNumBytesShouldFail() throws Http2Exception {
        assertFalse(controller.consumeBytes(connection.stream(STREAM_ID), -1));
    }

    private void testRatio(float ratio, int newDefaultWindowSize, int newStreamId, boolean setStreamRatio)
            throws Http2Exception {
        int delta = newDefaultWindowSize - DEFAULT_WINDOW_SIZE;
        controller.incrementWindowSize(stream(0), delta);
        Http2Stream stream = connection.local().createStream(newStreamId, false);
        if (setStreamRatio) {
            controller.windowUpdateRatio(stream, ratio);
        }
        controller.incrementWindowSize(stream, delta);
        reset(frameWriter);
        try {
            int data1 = (int) (newDefaultWindowSize * ratio) + 1;
            int data2 = (int) (DEFAULT_WINDOW_SIZE * updateRatio) >> 1;
            receiveFlowControlledFrame(STREAM_ID, data2, 0, false);
            receiveFlowControlledFrame(newStreamId, data1, 0, false);
            verifyWindowUpdateNotSent(STREAM_ID);
            verifyWindowUpdateNotSent(newStreamId);
            verifyWindowUpdateNotSent(CONNECTION_STREAM_ID);
            assertEquals(DEFAULT_WINDOW_SIZE - data2, window(STREAM_ID));
            assertEquals(newDefaultWindowSize - data1, window(newStreamId));
            assertEquals(newDefaultWindowSize - data2 - data1, window(CONNECTION_STREAM_ID));
            assertFalse(consumeBytes(STREAM_ID, data2));
            assertTrue(consumeBytes(newStreamId, data1));
            verifyWindowUpdateNotSent(STREAM_ID);
            verifyWindowUpdateSent(newStreamId, data1);
            verifyWindowUpdateSent(CONNECTION_STREAM_ID, data1 + data2);
            assertEquals(DEFAULT_WINDOW_SIZE - data2, window(STREAM_ID));
            assertEquals(newDefaultWindowSize, window(newStreamId));
            assertEquals(newDefaultWindowSize, window(CONNECTION_STREAM_ID));
        } finally {
            connection.stream(newStreamId).close();
        }
    }

    private static int getWindowDelta(int initialSize, int windowSize, int dataSize) {
        int newWindowSize = windowSize - dataSize;
        return initialSize - newWindowSize;
    }

    private void receiveFlowControlledFrame(int streamId, int dataSize, int padding,
                                            boolean endOfStream) throws Http2Exception {
        receiveFlowControlledFrame(stream(streamId), dataSize, padding, endOfStream);
    }

    private void receiveFlowControlledFrame(Http2Stream stream, int dataSize, int padding,
                                            boolean endOfStream) throws Http2Exception {
        final ByteBuf buf = dummyData(dataSize);
        try {
            controller.receiveFlowControlledFrame(stream, buf, padding, endOfStream);
        } finally {
            buf.release();
        }
    }

    private static ByteBuf dummyData(int size) {
        final ByteBuf buffer = Unpooled.buffer(size);
        buffer.writerIndex(size);
        return buffer;
    }

    private boolean consumeBytes(int streamId, int numBytes) throws Http2Exception {
        return controller.consumeBytes(stream(streamId), numBytes);
    }

    private void verifyWindowUpdateSent(int streamId, int windowSizeIncrement) {
        verify(frameWriter).writeWindowUpdate(eq(ctx), eq(streamId), eq(windowSizeIncrement), eq(promise));
    }

    private void verifyWindowUpdateNotSent(int streamId) {
        verify(frameWriter, never()).writeWindowUpdate(eq(ctx), eq(streamId), anyInt(), eq(promise));
    }

    private void verifyWindowUpdateNotSent() {
        verify(frameWriter, never()).writeWindowUpdate(any(ChannelHandlerContext.class), anyInt(), anyInt(),
                any(ChannelPromise.class));
    }

    private int window(int streamId) throws Http2Exception {
        return controller.windowSize(stream(streamId));
    }

    private Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }
}
