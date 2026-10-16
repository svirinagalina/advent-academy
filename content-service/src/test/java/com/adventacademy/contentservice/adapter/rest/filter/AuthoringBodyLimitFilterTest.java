package com.adventacademy.contentservice.adapter.rest.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class AuthoringBodyLimitFilterTest {
    private final AuthoringBodyLimitFilter filter = new AuthoringBodyLimitFilter();
    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("PUT", "/api/lessons/11111111-1111-1111-1111-111111111111/content/draft");
    }
    @Test
    void boundedRequestCanStillBeDeserializedAfterTheFilterReadsIt() throws Exception {
        var request=request();
        byte[] body="{\"markdown\":\"# Урок\",\"version\":0}".getBytes(StandardCharsets.UTF_8);
        request.setContent(body);
        var called=new AtomicBoolean();
        filter.doFilter(request,new MockHttpServletResponse(),(input,output)->{
            assertThat(input.getInputStream().readAllBytes()).isEqualTo(body); called.set(true);
        });
        assertThat(called).isTrue();
    }
    @Test
    void oversizedBodyIsRefusedBeforeJsonDeserializationEvenWithoutContentLength() throws Exception {
        var request=new MockHttpServletRequest("POST",request().getRequestURI()+"/preview") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[32*1024*1024+1]);
        var response=new MockHttpServletResponse();var called=new AtomicBoolean();
        filter.doFilter(request,response,(input,output)->called.set(true));
        assertThat(response.getStatus()).isEqualTo(413); assertThat(called).isFalse();
    }
}
