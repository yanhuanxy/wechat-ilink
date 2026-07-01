package com.github.wechat.ilink.bot.llm;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseParserTest {

    @Test
    void parse_validEvents_extractsAllTokens() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"!\"}}]}\n\n"
                + "data: [DONE]\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError");
            }
        });

        assertEquals(3, tokens.size());
        assertEquals("Hello", tokens.get(0));
        assertEquals(" world", tokens.get(1));
        assertEquals("!", tokens.get(2));
        assertEquals("Hello world!", completeResponse[0]);
    }

    @Test
    void parse_deltaWithoutContent_skipsGracefully() {
        String sse = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                + "data: [DONE]\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError");
            }
        });

        assertEquals(1, tokens.size());
        assertEquals("Hi", tokens.get(0));
        assertEquals("Hi", completeResponse[0]);
    }

    @Test
    void parse_emptyDelta_skipsGracefully() {
        String sse = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError");
            }
        });

        assertTrue(tokens.isEmpty());
        assertEquals("", completeResponse[0]);
    }

    @Test
    void parse_streamInterrupted_callsOnErrorWithPartial() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] errorPartial = {null};
        Throwable[] error = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse)) {
            private int readCount = 0;

            @Override
            public String readLine() throws IOException {
                readCount++;
                if (readCount > 2) {
                    throw new IOException("connection lost");
                }
                return super.readLine();
            }
        };

        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                fail("Should not call onComplete on error");
            }

            @Override
            public void onError(Throwable t) {
                error[0] = t;
            }
        });

        assertEquals(1, tokens.size());
        assertEquals("Partial", tokens.get(0));
        assertNotNull(error[0]);
        assertTrue(error[0] instanceof IOException);
    }

    @Test
    void parse_invalidJsonEvent_skipsAndContinues() {
        String sse = "data: {invalid json}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\n"
                + "data: [DONE]\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError for single bad event");
            }
        });

        assertEquals(1, tokens.size());
        assertEquals("OK", tokens.get(0));
        assertEquals("OK", completeResponse[0]);
    }

    @Test
    void parse_commentLines_ignored() {
        String sse = ": this is a comment\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
                + "data: [DONE]\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError");
            }
        });

        assertEquals(1, tokens.size());
        assertEquals("Hello", completeResponse[0]);
    }

    @Test
    void parse_noDoneSignal_completesOnStreamEnd() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"End\"}}]}\n\n";

        List<String> tokens = new ArrayList<String>();
        String[] completeResponse = {null};

        BufferedReader reader = new BufferedReader(new StringReader(sse));
        SseParser.parse(reader, new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(String fullResponse) {
                completeResponse[0] = fullResponse;
            }

            @Override
            public void onError(Throwable t) {
                fail("Should not call onError");
            }
        });

        assertEquals(1, tokens.size());
        assertEquals("End", completeResponse[0]);
    }
}
