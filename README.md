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