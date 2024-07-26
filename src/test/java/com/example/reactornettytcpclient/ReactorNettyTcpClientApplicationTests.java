package com.example.reactornettytcpclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@SpringBootTest
class ReactorNettyTcpClientApplicationTests {

    @Test
    void contextLoads() {
        // Create a Flux that emits signals at a fixed interval
        Flux<Long> intervalFlux = Flux.interval(Duration.ofSeconds(3));

        // Transform each tick into two separate signals with a delay
        Flux<String> signalFlux = intervalFlux.flatMap(tick -> {
            // Create two separate signals for each interval tick
            Mono<String> signal1 = Mono.just("Signal1 for tick " + tick);
            Mono<String> signal2 = Mono.just("Signal2 for tick " + tick);

            // Combine the two signals into a single Flux with a delay
            return Flux.concat(signal1, signal2)
                    .delayElements(Duration.ofMillis(1000)); // Add a delay of 500 milliseconds between emissions
        });

        // Subscribe to the combined Flux and print each emitted signal
        signalFlux.subscribe(signal -> {
            System.out.println("Received: " + signal);
        }, error -> {
            System.err.println("Error: " + error);
        }, () -> {
            System.out.println("Stream complete");
        });

        // Keep the application running for demonstration purposes
        try {
            Thread.sleep(10000); // Adjust duration as needed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
