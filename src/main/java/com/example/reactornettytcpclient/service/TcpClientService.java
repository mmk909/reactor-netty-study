package com.example.reactornettytcpclient.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;

import reactor.netty.Connection;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TcpClientService {

    private final List<String> messages = List.of("r", "m", "h");

    private final Map<String, Disposable> activeConnections = new ConcurrentHashMap<>();
    private final HttpClientService httpClientService;


    @Autowired
    public TcpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public void createReadOnlyTcpClient(String clientId, String host, int port, String httpEndpoint) {
        Disposable conn = TcpClient.create()
                .host(host)
                .port(port)
                .handle((inbound, outbound) -> {
                    return inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .flatMap(data -> {
                                System.out.println("Received from " + httpEndpoint + " :" + clientId + ": " + data);

                                // Simulate sending to an HTTP endpoint
                                String response = "Response to " + httpEndpoint + " :" + clientId + ": " + data;
                                // For demonstration purposes, assuming you have an HttpClientService
                                httpClientService.sendHttpPost(httpEndpoint, response);
//                                System.out.println(response);

                                return Flux.empty(); // Continue listening indefinitely
                            })
                            .then();
                })
                .connect()
                .doOnSuccess(connection -> {
                    System.out.println("Connected TCP client for " + clientId);
//                    activeConnections.put(clientId, connection);
                })
                .subscribe();
        activeConnections.put(clientId, conn);
    }


    public void createWriteReadTcpClient(String clientId, String host, int port, String httpEndpoint) {
        destroyTcpClient(clientId);

        Disposable conn = TcpClient.create()
                .host(host)
                .port(port)
                .connect()
                .doOnSuccess(connection -> {
                    System.out.println("Connected TCP client for " + clientId);
                })
                .flatMapMany(connection -> {
                    // Create a Flux to periodically send messages
                    Flux<Void> periodicWrites = Flux.interval(Duration.ofSeconds(1))
                            .flatMap(tick ->
                                    connection.outbound().sendString(Mono.just("r")).then()
                            )
                            .doOnCancel(() -> System.out.println("Periodic writes canceled"));

                    // Read responses from the server
                    Flux<String> responses = connection.inbound().receive().asString();

                    // Merge the periodic writes and responses
                    return Flux.merge(periodicWrites, responses)
                            .doFinally(signalType -> {
                                if (signalType == SignalType.CANCEL) {
                                    System.out.println("Connection was canceled, stopping periodic writes.");
                                } else if (signalType == SignalType.ON_COMPLETE) {
                                    System.out.println("Connection completed normally.");
                                } else if (signalType == SignalType.ON_ERROR) {
                                    System.out.println("Connection encountered an error.");
                                }
                            });
                })
                .doOnNext(response -> System.out.println("Received: " + response))
                .subscribe();
        activeConnections.put(clientId, conn);
    }

    public void destroyTcpClient(String clientId) {
        Disposable connection = activeConnections.get(clientId);
        if (connection != null) {
            connection.dispose();
            activeConnections.remove(clientId);
            System.out.println("Disconnected TCP client for " + clientId);
        }
    }

    @PreDestroy
    public void shutdownAllClients() {
        activeConnections.values().forEach(Disposable::dispose);
        activeConnections.clear();
        System.out.println("Shutdown all TCP clients");
    }
}

