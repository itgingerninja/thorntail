/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.jaeger.deployment;

import io.jaegertracing.Configuration.CodecConfiguration;
import io.jaegertracing.internal.propagation.B3TextMapCodec;
import io.opentracing.propagation.Format.Builtin;
import java.text.NumberFormat;
import java.text.ParseException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.jaegertracing.Configuration;
import io.opentracing.util.GlobalTracer;
import org.jboss.logging.Logger;

import static io.jaegertracing.Configuration.JAEGER_AGENT_HOST;
import static io.jaegertracing.Configuration.JAEGER_AGENT_PORT;
import static io.jaegertracing.Configuration.JAEGER_ENDPOINT;
import static io.jaegertracing.Configuration.JAEGER_PASSWORD;
import static io.jaegertracing.Configuration.JAEGER_REPORTER_FLUSH_INTERVAL;
import static io.jaegertracing.Configuration.JAEGER_REPORTER_LOG_SPANS;
import static io.jaegertracing.Configuration.JAEGER_REPORTER_MAX_QUEUE_SIZE;
import static io.jaegertracing.Configuration.JAEGER_SAMPLER_MANAGER_HOST_PORT;
import static io.jaegertracing.Configuration.JAEGER_SAMPLER_PARAM;
import static io.jaegertracing.Configuration.JAEGER_SAMPLER_TYPE;
import static io.jaegertracing.Configuration.JAEGER_SERVICE_NAME;
import static io.jaegertracing.Configuration.JAEGER_USER;
import static io.jaegertracing.Configuration.ReporterConfiguration;
import static io.jaegertracing.Configuration.SenderConfiguration;

/**
 * @author Juraci Paixão Kröhling
 */
@WebListener
public class JaegerInitializer implements ServletContextListener {
    private static final Logger logger = Logger.getLogger(JaegerInitializer.class);

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext sc = servletContextEvent.getServletContext();

        String serviceName = getProperty(sc, JAEGER_SERVICE_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            logger.warn("No Service Name set. Using default. Please change it.");
            serviceName = "thorntail/unknown";
        }

        Configuration configuration = new Configuration(serviceName)
                .withSampler(
                        new Configuration.SamplerConfiguration()
                                .withType(
                                        getProperty(sc, JAEGER_SAMPLER_TYPE))
                                .withParam(
                                        getPropertyAsNumber(sc, JAEGER_SAMPLER_PARAM))
                                .withManagerHostPort(
                                        getProperty(sc, JAEGER_SAMPLER_MANAGER_HOST_PORT)))
                .withReporter(
                        new ReporterConfiguration()
                                .withLogSpans(
                                        getPropertyAsBoolean(sc, JAEGER_REPORTER_LOG_SPANS))
                                .withSender(
                                        new SenderConfiguration()
                                                .withAuthUsername(getProperty(sc, JAEGER_USER))
                                                .withAuthPassword(getProperty(sc, JAEGER_PASSWORD))
                                                .withAgentHost(getProperty(sc, JAEGER_AGENT_HOST))
                                                .withAgentPort(getPropertyAsInt(sc, JAEGER_AGENT_PORT)))
                                .withFlushInterval(
                                        getPropertyAsInt(sc, JAEGER_REPORTER_FLUSH_INTERVAL))
                                .withMaxQueueSize(
                                        getPropertyAsInt(sc, JAEGER_REPORTER_MAX_QUEUE_SIZE)
                                )
                );

        String remoteEndpoint = getProperty(sc, JAEGER_ENDPOINT);
        if (remoteEndpoint != null && remoteEndpoint.trim().length() > 0) {
            configuration.getReporter()
                    .withSender(new SenderConfiguration()
                                        .withEndpoint(remoteEndpoint));
        }

        String enableB3HeaderPropagation = getProperty(sc, "enableB3HeaderPropagation");
        if (enableB3HeaderPropagation != null && Boolean.parseBoolean(enableB3HeaderPropagation)) {
            logger.info("Enabling B3 Header Propagation for Jaeger");
            CodecConfiguration codecConfiguration = new CodecConfiguration();
            codecConfiguration.withCodec(Builtin.HTTP_HEADERS, new B3TextMapCodec.Builder().build());
            codecConfiguration.withCodec(Builtin.TEXT_MAP, new B3TextMapCodec.Builder().build());
            configuration.withCodec(codecConfiguration);
        }

        GlobalTracer.register(configuration.getTracer());
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    }

    private static String getProperty(ServletContext sc, String name) {
        return sc.getInitParameter(name);
    }

    private static Integer getPropertyAsInt(ServletContext sc, String name) {
        String value = getProperty(sc, name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse integer for property '" + name + "' with value '" + value + "'", e);
            }
        }
        return null;
    }

    private static Boolean getPropertyAsBoolean(ServletContext sc, String name) {
        String value = getProperty(sc, name);
        if (value != null && !value.isEmpty()) {
            return Boolean.valueOf(value);
        }
        return null;
    }

    private static Number getPropertyAsNumber(ServletContext sc, String name) {
        String value = getProperty(sc, name);
        if (value != null && !value.isEmpty()) {
            try {
                return NumberFormat.getInstance().parse(value);
            } catch (ParseException e) {
                logger.error("Failed to parse number for property '" + name + "' with value '" + value + "'", e);
            }
        }
        return null;
    }
}
