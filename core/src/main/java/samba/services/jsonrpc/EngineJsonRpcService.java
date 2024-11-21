/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package samba.services.jsonrpc;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;


import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import samba.services.jsonrpc.health.HealthService;
import samba.services.jsonrpc.reponse.JsonRpcMethod;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

//TODO Tracker, OpenTelemetrySystem,  dataDir          The data directory where requests can be buffered
//TODO websocket, authentication, traceFormacts, scheduler,
public class EngineJsonRpcService {

    private static final Logger LOG = LoggerFactory.getLogger(EngineJsonRpcService.class);

    private static final String SPAN_CONTEXT = "span_context";
    private static final InetSocketAddress EMPTY_SOCKET_ADDRESS = new InetSocketAddress("0.0.0.0", 0);
    private static final String APPLICATION_JSON = "application/json";

    private final Vertx vertx;
    private final JsonRpcConfiguration config;
    private final Map<String, JsonRpcMethod> rpcMethods;
    private final LabelledMetric<OperationTimer> requestTimer;
    private final int maxActiveConnections;
    private final AtomicInteger activeConnectionsCount = new AtomicInteger();


    private HttpServer httpServer;
    private final HealthService livenessService;
    private final HealthService readinessService;
    private final NatService natService;

    private final MetricsSystem metricsSystem;


    /**
     * Construct a EngineJsonRpcService to handle either http
     *
     * @param vertx            The vertx process that will be running this service
     * @param config           Configuration for the rpc methods being loaded
     * @param metricsSystem    The metrics service that activities should be reported to
     * @param natService       The NAT environment manager.
     * @param methods          The json rpc methods that should be enabled
     * @param livenessService  A service responsible for reporting whether this node is live
     * @param readinessService A service responsible for reporting whether this node has fully started
     */
    public EngineJsonRpcService(
            final Vertx vertx,
            final JsonRpcConfiguration config,
            final MetricsSystem metricsSystem,
            final NatService natService,
            final Map<String, JsonRpcMethod> methods,
            final HealthService livenessService,
            final HealthService readinessService) {
        this.requestTimer = metricsSystem.createLabelledTimer(BesuMetricCategory.RPC, "request_time", "Time taken to process a JSON-RPC request", "methodName");
        JsonRpcProcessor jsonRpcProcessor = new BaseJsonRpcProcessor();
        final JsonRpcExecutor jsonRpcExecutor = new JsonRpcExecutor(jsonRpcProcessor, methods);
        validateConfig(config);
        this.config = config;
        this.vertx = vertx;
        this.natService = natService;
        this.rpcMethods = methods;
        this.livenessService = livenessService;
        this.readinessService = readinessService;
        this.maxActiveConnections = config.getMaxActiveConnections();
        this.metricsSystem = metricsSystem;
    }

    public CompletableFuture<Void> start() {
        LOG.info("Starting JSON-RPC service on {}:{}", config.getHost(), config.getPort());
        LOG.debug("max number of active connections {}", maxActiveConnections);
        final CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        try {
            // Create the HTTP server and a router object.
            httpServer = vertx.createHttpServer(getHttpServerOptions());
            //httpServer.webSocketHandler(webSocketHandler()); TODO implement also a websocketHandler
            httpServer.connectionHandler(connectionHandler());

            httpServer
                    .requestHandler(buildRouter())
                    .listen(
                            res -> {
                                if (!res.failed()) {
                                    resultFuture.complete(null);
                                    config.setPort(httpServer.actualPort());
                                    LOG.info("JSON-RPC service started and listening on {}:{}", config.getHost(), config.getPort());
                                    natService.ifNatEnvironment(
                                            NatMethod.UPNP,
                                            natManager ->
                                                    ((UpnpNatManager) natManager)
                                                            .requestPortForward(
                                                                    config.getPort(), NetworkProtocol.TCP, NatServiceType.JSON_RPC));

                                    return;
                                }

                                httpServer = null;
                                resultFuture.completeExceptionally(getFailureException(res.cause()));
                            });
        } catch (final Exception exception) {
            httpServer = null;
            resultFuture.completeExceptionally(new RuntimeException(String.format("JSON-RPC listener failed to start: %s", exception.getMessage())));
        }

        return resultFuture;
    }

    public CompletableFuture<Void> stop() {
        if (httpServer == null) {
            return CompletableFuture.completedFuture(null);
        }

        final CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        httpServer.close(
                res -> {
                    if (res.failed()) {
                        resultFuture.completeExceptionally(res.cause());
                    } else {
                        httpServer = null;
                        resultFuture.complete(null);
                    }
                });
        return resultFuture;
    }

    private Handler<HttpConnection> connectionHandler() {
        return connection -> {
            if (activeConnectionsCount.get() >= maxActiveConnections) {
                LOG.warn("Rejecting new connection from {}. Max {} active connections limit reached.", connection.remoteAddress(), activeConnectionsCount.getAndIncrement());
                connection.close();
            } else {
                LOG.info("Opened connection from {}. Total of active connections: {}/{}", connection.remoteAddress(), activeConnectionsCount.incrementAndGet(), maxActiveConnections);
            }
            connection.closeHandler(c -> LOG.debug("Connection closed from {}. Total of active connections: {}/{}", connection.remoteAddress(), activeConnectionsCount.decrementAndGet(), maxActiveConnections));
        };
    }


    private void validateConfig(final JsonRpcConfiguration config) {
        checkArgument(config.getPort() == 0 || NetworkUtility.isValidPort(config.getPort()), "Invalid port configuration.");
        checkArgument(config.getHost() != null, "Required host is not configured.");
        checkArgument(config.getMaxActiveConnections() > 0, "Invalid max active connections configuration.");
    }

    private Router buildRouter() {
        // Handle json rpc requests
        final Router router = Router.router(vertx);
        // Verify Host header to avoid rebind attack.
        router.route().handler(denyRouteToBlockedHost());
        router.errorHandler(403, new Logging403ErrorHandler());
        router.route().handler(CorsHandler.create(buildCorsRegexFromConfig()).allowedHeader("*").allowedHeader("content-type"));
        router.route("/").method(HttpMethod.GET).handler(this::handleEmptyRequest);
        router.route(HealthService.LIVENESS_PATH).method(HttpMethod.GET).handler(livenessService::handleRequest);
        router.route(HealthService.READINESS_PATH).method(HttpMethod.GET).handler(readinessService::handleRequest);
        Route mainRoute = router.route("/").method(HttpMethod.POST).produces(APPLICATION_JSON);
        mainRoute.handler(HandlerFactory.jsonRpcParser()).handler(HandlerFactory.timeout(new TimeoutOptions(config.getHttpTimeoutSec()), rpcMethods));
        return router;
    }


    private HttpServerOptions getHttpServerOptions() {
        return new HttpServerOptions()
                .setHost(config.getHost())
                .setPort(config.getPort())
                .setHandle100ContinueAutomatically(true)
                .setCompressionSupported(true);
    }


    private Throwable getFailureException(final Throwable listenFailure) {

        JsonRpcServiceException servFail =
                new JsonRpcServiceException(
                        String.format(
                                "Failed to bind Ethereum JSON-RPC listener to %s:%s: %s",
                                config.getHost(), config.getPort(), listenFailure.getMessage()));
        servFail.initCause(listenFailure);

        return servFail;
    }

    private Handler<RoutingContext> denyRouteToBlockedHost() {
        return event -> {
            final Optional<String> hostHeader = getAndValidateHostHeader(event);
            if (config.getHostsAllowlist().contains("*")
                    || (hostHeader.isPresent() && hostIsInAllowlist(hostHeader.get()))) {
                event.next();
            } else {
                final HttpServerResponse response = event.response();
                if (!response.closed()) {
                    response
                            .setStatusCode(403)
                            .putHeader("Content-Type", "application/json; charset=utf-8")
                            .end("{\"message\":\"Host not authorized.\"}");
                }
            }
        };
    }

    private Optional<String> getAndValidateHostHeader(final RoutingContext event) {
        final HostAndPort hostAndPort = event.request().authority();
        return Optional.ofNullable(hostAndPort).map(HostAndPort::host);
    }

    private boolean hostIsInAllowlist(final String hostHeader) {
        if (config.getHostsAllowlist().contains("*")
                || config.getHostsAllowlist().stream()
                .anyMatch(allowlistEntry -> allowlistEntry.equalsIgnoreCase(hostHeader))) {
            return true;
        } else {
            LOG.trace("Host not in allowlist: '{}'", hostHeader);
            return false;
        }
    }

    public InetSocketAddress socketAddress() {
        if (httpServer == null) {
            return EMPTY_SOCKET_ADDRESS;
        }
        return new InetSocketAddress(config.getHost(), httpServer.actualPort());
    }

    private void handleEmptyRequest(final RoutingContext routingContext) {
        routingContext.response().setStatusCode(201).end();
    }

    private String buildCorsRegexFromConfig() {
        if (config.getCorsAllowedDomains().isEmpty()) {
            return "";
        }
        if (config.getCorsAllowedDomains().contains("*")) {
            return ".*";
        } else {
            final StringJoiner stringJoiner = new StringJoiner("|");
            config.getCorsAllowedDomains().stream().filter(s -> !s.isEmpty()).forEach(stringJoiner::add);
            return stringJoiner.toString();
        }
    }
}
