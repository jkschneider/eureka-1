package io.pivotal.eureka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.netflix.eureka.EurekaConstants;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaApplication.class, args);
    }
}

@Configuration
class MetricsFilterConfiguration {
    @Bean
    public FilterRegistrationBean eurekaMetricsFilter(MeterRegistry registry) {
        FilterRegistrationBean bean = new FilterRegistrationBean();
        bean.setFilter(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) {
            }

            /**
             * This captures requests that are ultimately going to be handled by eureka internals.
             */
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                Timer.Sample sample = Timer.start(registry);
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                chain.doFilter(request, response);
                sample.stop(registry.timer("eureka.server.requests",
                        "method", httpRequest.getMethod(),
                        "status", Integer.toString(httpResponse.getStatus()),
                        "uri", httpRequest.getRequestURI().replaceAll("/eureka/apps/.+", "/eureka/apps/**")));
            }

            @Override
            public void destroy() {
            }
        });
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setUrlPatterns(
                Collections.singletonList(EurekaConstants.DEFAULT_PREFIX + "/*"));

        return bean;
    }
}