package com.example.reactornettytcpclient.service;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class HttpClientService {

    private final WebClient webClient;

    public HttpClientService() {
        this.webClient = WebClient.create();
    }

    public void sendHttpPost(String url, String body) {
        webClient.post()
                .uri(url)
                .body(Mono.just(body), String.class)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> System.out.println("HTTP Response: " + response))
                .subscribe();
    }
}
