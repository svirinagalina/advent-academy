package com.adventacademy.contentservice.adapter.rest.filter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class ContentWorkLimiterTest {
    @Test void overloadIsControlledAndSlotsAreReleasedAfterFailure() throws Exception {
        var filter=new ContentWorkLimiter(1,1);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->{
                filter.doFilter(new MockHttpServletRequest("GET","/api/assets/one"),new MockHttpServletResponse(),(request,response)->{
                    entered.countDown();try {release.await(10,TimeUnit.SECONDS);} catch(InterruptedException e){Thread.currentThread().interrupt();}
                });return true;
            });
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var overloaded=new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET","/api/assets/two"),overloaded,(request,response)->fail("Overloaded work must not reach storage"));
            assertThat(overloaded.getStatus()).isEqualTo(429);
            assertThat(overloaded.getHeader("Retry-After")).isEqualTo("1");
            release.countDown();assertThat(first.get(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->filter.doFilter(new MockHttpServletRequest("GET","/api/assets/three"),new MockHttpServletResponse(),
                    (request,response)->{throw new IOException("synthetic disconnect");})).isInstanceOf(IOException.class);
            var recovered=new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET","/api/assets/four"),recovered,(request,response)->response.setContentType("application/octet-stream"));
            assertThat(recovered.getStatus()).isEqualTo(200);
        } finally {release.countDown();pool.shutdownNow();}
    }
}
