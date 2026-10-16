package com.adventacademy.userservice.infrastructure.ratelimit;

import com.adventacademy.security.SignedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class TrustedClientRateLimitTest {
    @Test void distinctSignedClientsDoNotShareGatewayQuotaAndForgeryIsRefused() throws Exception {
        String key="isolated-bff-request-key-at-least-32-bytes";
        var counts=new HashMap<String,Integer>();
        RateLimiter limiter=(scope,identity,limit,window) -> counts.merge(scope+":"+identity,1,Integer::sum)<=limit;
        var filter=new AuthRateLimitFilter(limiter,new AuthRateLimitProperties(true,Duration.ofHours(1),20,10,10,5,3,5,3),new ObjectMapper());
        ReflectionTestUtils.setField(filter,"bffRequestKey",key);
        for(int i=0;i<21;i++) {
            var request=request("a".repeat(64),"user"+i+"@example.test");
            request.addHeader("X-Advent-Client-Proof",SignedRequest.sign(key,"client","/api/auth/login:"+"a".repeat(64)));
            var response=new MockHttpServletResponse(); filter.doFilter(request,response,new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(i<20 ? 200 : 429);
        }
        var independent=request("b".repeat(64),"other@example.test");
        independent.addHeader("X-Advent-Client-Proof",SignedRequest.sign(key,"client","/api/auth/login:"+"b".repeat(64)));
        var response=new MockHttpServletResponse();filter.doFilter(independent,response,new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
        var forged=request("c".repeat(64),"forged@example.test");forged.addHeader("X-Advent-Client-Proof","0.forged");
        response=new MockHttpServletResponse();filter.doFilter(forged,response,new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }
    private MockHttpServletRequest request(String bucket,String email) {
        var request=new MockHttpServletRequest("POST","/api/auth/login");request.setRemoteAddr("172.19.0.2");
        request.addHeader("X-Advent-Client",bucket);
        request.setContent(("{\"email\":\""+email+"\",\"password\":\"test\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
