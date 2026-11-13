package dev.logtriage.demo.common;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Every RestClient built from the auto-configured builder forwards the
 * transaction id, logs the outbound call, and uses explicit timeouts.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionIdPropagation {

    private static final Logger log = LoggerFactory.getLogger("outbound");

    @Bean
    RestClientCustomizer demoRestClientCustomizer(
            @Value("${demo.http.connect-timeout:1s}") Duration connectTimeout,
            @Value("${demo.http.read-timeout:3s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder -> builder.requestFactory(factory).requestInterceptor(interceptor());
    }

    private static ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            String txId = MDC.get(TransactionIdFilter.MDC_KEY);
            if (txId != null) {
                request.getHeaders().set(TransactionIdFilter.HEADER, txId);
            }
            long start = System.nanoTime();
            try {
                var response = execution.execute(request, body);
                log.info("Outbound {} {} -> {} in {}ms", request.getMethod(), request.getURI(),
                        response.getStatusCode().value(), elapsedMs(start));
                return response;
            } catch (IOException e) {
                log.warn("Outbound {} {} failed after {}ms: {}", request.getMethod(), request.getURI(),
                        elapsedMs(start), e.toString());
                throw e;
            }
        };
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
