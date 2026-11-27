package com.hsdc.copilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hsdc.copilot.llm.CancelToken;
import com.hsdc.copilot.web.sse.ProgressEvent;

import tools.jackson.databind.json.JsonMapper;

class SseEventWriterTest {

    /** An emitter whose writes fail, like one whose client has disconnected. */
    static final class BrokenEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("Broken pipe");
        }

        @Override
        public void send(Object object, MediaType mediaType) throws IOException {
            throw new IOException("Broken pipe");
        }
    }

    @Test
    void aFailedWriteCancelsTheTurnAndLaterSendsAreSkipped() {
        CancelToken token = new CancelToken();
        SseEventWriter writer = new SseEventWriter(new BrokenEmitter(), JsonMapper.builder().build(), token);

        assertThat(writer.send("progress", new ProgressEvent(ProgressEvent.Step.UPLOAD_RECEIVED))).isFalse();
        assertThat(token.isCancelled()).isTrue();
        assertThat(writer.send("progress", new ProgressEvent(ProgressEvent.Step.ANALYZING_PHOTO))).isFalse();
    }

    @Test
    void aFailedHeartbeatCancelsTheTurn() {
        CancelToken token = new CancelToken();
        SseEventWriter writer = new SseEventWriter(new BrokenEmitter(), JsonMapper.builder().build(), token);

        writer.ping();

        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void completeIsIdempotentAndStopsFurtherSends() {
        CancelToken token = new CancelToken();
        SseEventWriter writer = new SseEventWriter(new SseEmitter(), JsonMapper.builder().build(), token);

        writer.complete();
        writer.complete();

        assertThat(writer.send("progress", new ProgressEvent(ProgressEvent.Step.UPLOAD_RECEIVED))).isFalse();
        assertThat(token.isCancelled()).isFalse();
    }
}
