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
import reactor.util.retry.Retry;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TcpClientService {

    private final List<String> messages = List.of("r", "m", "h");

    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Disposable> activeRetryMonos = new ConcurrentHashMap<>();

    private final HttpClientService httpClientService;


    @Autowired
    public TcpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public void createReadOnlyTcpClient(String clientId, String host, int port, String httpEndpoint) {
        TcpClient.create()
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
                    activeConnections.put(clientId, connection);
                })
                .subscribe();
//        activeConnections.put(clientId, conn);
    }


    public Mono<Void> createWriteReadTcpClient(String clientId, String host, int port, String httpEndpoint) {
        destroyTcpClient(clientId);

        return TcpClient.create()
                .host(host)
                .port(port)
                .connect()
                .doOnSuccess(connection -> {
                    System.out.println("Connected TCP client for " + clientId);
                    activeConnections.put(clientId, connection);
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
                .then();
//        activeConnections.put(clientId, conn);
    }

    private static boolean isRetryableError(Throwable throwable) {
        return throwable instanceof Exception;  // Customize as per your error handling needs
    }

    public void createWriteReadMultiTcpClient(String clientId, String host, int port, String httpEndpoint) {
        Retry retrySpec = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                .filter(throwable -> isRetryableError(throwable));
        Mono<Void> connectionMono = Mono.defer(()-> {
                  return  _createWriteReadMultiTcpClient(clientId,host,port,httpEndpoint);
        });
        connectionMono.retryWhen(retrySpec)
                .subscribe();

    }

    public Mono<Void> _createWriteReadMultiTcpClient(String clientId, String host, int port, String httpEndpoint) {
        destroyTcpClient(clientId);


        return TcpClient.create()
                .host(host)
                .port(port)
                .doOnDisconnected(connection -> {
                    Connection exist_conn = activeConnections.get(clientId);
                    if (exist_conn != null) {
                        System.out.println("error happened!");
                        activeConnections.remove(clientId);
                        exist_conn.dispose();
                        createWriteReadMultiTcpClient(clientId,host,port,httpEndpoint);
                    }
                })
                .connect()
                .doOnSuccess(connection -> {
                    System.out.println("Connected TCP client for " + clientId);
                    activeConnections.put(clientId, connection);

                })
//                .onErrorResume(error->{
//                    System.out.println(error);
//                    return null;
//                })
//                .doOnError(error -> {
//                    System.err.println("Connection failed: " + error);
//                    // Retry the connection on error
//                    System.out.println("Attempting to reconnect...");
//                })
                .flatMapMany(connection -> {
                    // Create a Flux to periodically send messages
                    Flux<String> periodicWrites = Flux.interval(Duration.ofSeconds(3))
                            .flatMap(tick -> {
                                List<Mono<String>> list = messages.stream().map(cmd -> {
                                    return connection.outbound().sendString(Mono.just(cmd)).then().onErrorResume(error->{
                                        System.out.println(error);
                                        return null;
                                    }).doOnError(error-> System.out.println(error)).then(Mono.delay(Duration.ofMillis(200))).then(Mono.just(cmd));
                                }).collect(Collectors.toList());
                                return Flux.concat(list).delayElements(Duration.ofMillis(200));
                            })
                            .doOnCancel(() -> System.out.println("Periodic writes canceled"));

                    // Read responses from the server
                    Flux<String> responses = connection.inbound().receive().asString().onErrorResume(error->{
                        System.out.println(error);
                        return null;
                    }).doOnError(error-> System.out.println(error));

                    // Merge the periodic writes and responses
                    return periodicWrites.zipWith(responses,(a,b)-> a + ":" + b)
                            .onErrorResume(error->{
                                System.out.println(error);
                                return null;
                            })
                            .doOnError(error->{
                                System.out.println(error);
                            })
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
                .then();
    }

    public void createReadWriteMultiTcpClient(String clientId, String host, int port, String httpEndpoint) {
        Retry retrySpec = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(60))
                .filter(throwable -> isRetryableError(throwable))
                .doBeforeRetry(retrySignal -> {
                    System.out.println("Retrying due to: " + retrySignal.failure().getMessage());
                    System.out.println("Retry attempt: " + retrySignal.totalRetries() + ", delay: " + retrySignal.totalRetriesInARow());
                });
        Mono<Void> connectionMono = Mono.defer(()-> {
            return  _createReadWriteMultiTcpClient(clientId,host,port,httpEndpoint);
        });
        Disposable disposable = connectionMono.retryWhen(retrySpec)
                .doOnSubscribe(subscription -> System.out.println("Subscription started for clientId: " + clientId))
                .doOnSuccess(aVoid -> System.out.println("Connection successful for clientId: " + clientId))
                .doOnError(error -> System.out.println("Connection failed for clientId: " + clientId + " with error: " + error.getMessage()))
                .doFinally(signalType -> System.out.println("retry final:" + signalType))
                .subscribe();
        activeRetryMonos.put(clientId, disposable);

    }

    public Mono<Void> _createReadWriteMultiTcpClient(String clientId, String host, int port, String httpEndpoint) {
        destroyTcpClient(clientId);


        return TcpClient.create()
                .host(host)
                .port(port)
                .doOnDisconnected(connection -> {
                    Connection exist_conn = activeConnections.get(clientId);
                    if (exist_conn != null) {
                        System.out.println("error happened!");
                        activeConnections.remove(clientId);
                        exist_conn.dispose();
                        createReadWriteMultiTcpClient(clientId,host,port,httpEndpoint);
                    }
                })
                .handle((in, out) -> {
                    Flux<Void> responses = in.receive()
                            .asString()
                            .map(message -> {
                                System.out.println("Received message: " + message);

                                // Process message and prepare response
                                String response;
                                if ("Hello".equals(message)) {
                                    response = "Hello, server!";
                                } else if ("How are you?".equals(message)) {
                                    response = "I'm fine, thank you!";
                                } else {
                                    response = "Sorry, I didn't understand.";
                                }

                                return response;
                            })
                            .flatMap(response -> out.sendString(Flux.just(response)));

                    return responses;
                })
                .connect()
                .doOnSuccess(connection -> {
                    System.out.println("Connected TCP client for " + clientId);
                    activeConnections.put(clientId, connection);

                })
                .doOnNext(response -> System.out.println("Received: " + response))
                .then();
    }

    public void destroyTcpClientRetry(String clientId) {
        Disposable disposable = activeRetryMonos.get(clientId);
        if (disposable != null) {
            activeRetryMonos.remove(clientId);
            disposable.dispose();
            System.out.println("Disconnected TCP client for " + clientId);
        }
    }

    public void destroyTcpClient(String clientId) {

        Connection connection = activeConnections.get(clientId);
        if (connection != null) {
            activeConnections.remove(clientId);
            connection.dispose();
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

