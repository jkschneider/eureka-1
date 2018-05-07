## Eureka Load Test

The purpose of this project is to provide a test harness to load test Eureka server under various resource constraints. It consists of:
 
 1. `EurekaApplication`, a `@EnableEurekaServer` Spring Boot application running on Spring Cloud Edgware. This application can be deployed to
 CF using variants on `./cf-push.sh`.
 2. A `EurekaLoad` main class which is intended to be run pointed against the running server (modify the constant `EUREKA_HOST` in that class).
 
Additionally, run Prometheus locally using with static scrape targets pointing at both the server and load test harness:

```yml
global:
  scrape_interval:     15s # By default, scrape targets every 15 seconds.
  
scrape_configs:
  # The job name is added as a label `job=<job_name>` to any timeseries scraped from this config.
  - job_name: 'prometheus'

    # Override the global default and scrape targets from this job every 5 seconds.
    scrape_interval: 5s

    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'load-test'

    scrape_interval: 5s
    metrics_path: '/prometheus'
    static_configs:
      - targets: ['10.200.10.1:8080']

  - job_name: 'citi-82591'

    scrape_interval: 5s
    metrics_path: '/prometheus'
    static_configs:
      - targets: ['citi-82591.cfapps.io']

```

## A heartbeat PUT

```
PUT /eureka/apps/UNKNOWN/10.200.10.1?status=UP&lastDirtyTimestamp=1525720652715 HTTP/1.1
DiscoveryIdentity-Name: DefaultClient
DiscoveryIdentity-Version: 1.4
DiscoveryIdentity-Id: 10.200.10.1
Accept-Encoding: gzip
Content-Length: 0
Host: localhost:8761
Connection: Keep-Alive
User-Agent: Java-EurekaClient/v1.8.8

HTTP/1.1 200 
Content-Type: application/xml
Content-Length: 0
Date: Mon, 07 May 2018 19:18:02 GMT
```



PUT /eureka/apps/CLIENT-1/10.20.100.1?status=UP&lastDirtyTimestamp=1525720792508 HTTP/1.1
user-agent: ReactorNetty/0.7.6.RELEASE
transfer-encoding: chunked
host: localhost:8761
Content-Type: application/json
Accept: application/json
Accept-Encoding: gzip