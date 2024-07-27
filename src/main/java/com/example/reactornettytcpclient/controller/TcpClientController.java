package com.example.reactornettytcpclient.controller;

import com.example.reactornettytcpclient.service.HttpClientService;
import com.example.reactornettytcpclient.service.TcpClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tcp")
public class TcpClientController {

    private final TcpClientService tcpClientService;
    private final HttpClientService httpClientService;
    @Autowired
    public TcpClientController(TcpClientService tcpClientService, HttpClientService httpClientService) {
        this.tcpClientService = tcpClientService;
        this.httpClientService = httpClientService;
    }

    @PostMapping("/{clientId}")
    public void createTcpClient(@PathVariable String clientId,
                                @RequestBody Map param) {
        String host = (String) param.get("host");
        int port = (int)param.get("port");
        String httpEndpoint = (String)param.get("httpEndpoint");

//        tcpClientService.createReadOnlyTcpClient(clientId, host, port, httpEndpoint);
//        tcpClientService.createWriteReadMultiTcpClient(clientId, host, port, httpEndpoint);
        tcpClientService.createReadWriteMultiTcpClient(clientId, host, port, httpEndpoint);


    }

    @DeleteMapping("/{clientId}")
    public void destroyTcpClient(@PathVariable String clientId) {
        tcpClientService.destroyTcpClientRetry(clientId);
        tcpClientService.destroyTcpClient(clientId);
    }

    @PostMapping
    public void forward(@RequestBody String param){
        httpClientService.sendHttpPost("http://localhost:8081/test",param);
    }
}
