package load

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.CountDownLatch

object EurekaRegistrations {
    private const val NUM_CLIENTS = 1

    private val logger = LoggerFactory.getLogger(EurekaRegistrations::class.java)
    private val meterRegistry = Prometheus.setup()

    @JvmStatic
    fun main(args: Array<String>) {
        val client = WebClient
                .builder()
                .baseUrl(EUREKA_HOST)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .build()

        (1..NUM_CLIENTS).forEach { index ->
            val timestamp = System.currentTimeMillis()

            val body = """
                        {
                          "instance": {
                            "instanceId": "$CLIENT_IP",
                            "hostName": "$CLIENT_IP",
                            "app": "CLIENT$index",
                            "ipAddr": "$CLIENT_IP",
                            "status": "UP",
                            "overriddenStatus": "UNKNOWN",
                            "port": {
                              "${'$'}": 8080,
                              "@enabled": "true"
                            },
                            "securePort": {
                              "${'$'}": 443,
                              "@enabled": "false"
                            },
                            "countryId": 1,
                            "dataCenterInfo": {
                              "@class": "com.netflix.appinfo.InstanceInfo${'$'}DefaultDataCenterInfo",
                              "name": "MyOwn"
                            },
                            "leaseInfo": {
                              "renewalIntervalInSecs": 30,
                              "durationInSecs": 90,
                              "registrationTimestamp": 0,
                              "lastRenewalTimestamp": 0,
                              "evictionTimestamp": 0,
                              "serviceUpTimestamp": 0
                            },
                            "metadata": {
                              "management.port": "8080"
                            },
                            "homePageUrl": "http:\/\/$CLIENT_IP:8080\/",
                            "statusPageUrl": "http:\/\/$CLIENT_IP:8080\/actuator\/info",
                            "healthCheckUrl": "http:\/\/$CLIENT_IP:8080\/actuator\/health",
                            "vipAddress": "unknown",
                            "secureVipAddress": "unknown",
                            "isCoordinatingDiscoveryServer": "false",
                            "lastUpdatedTimestamp": "$timestamp",
                            "lastDirtyTimestamp": "$timestamp"
                          }
                        }
                        """.trimMargin()

            client.post()
                    .uri("/eureka/apps/{clientName}", "CLIENT$index")
                    .body(BodyInserters.fromObject(body))
                    .exchange().subscribe {
                        val status = it.statusCode().value()
                        meterRegistry.counter("eureka.requests", "uri", "/eureka/apps/{clientName}",
                                "status", status.toString()).increment()
                        if (status < 300) {
                            logger.debug("POST /eureka/apps/CLIENT$index $status")
                        } else {
                            val countDown = CountDownLatch(1)
                            it.bodyToMono<String>()
                                    .doOnTerminate {
                                        if (countDown.count > 0)
                                            logger.warn("POST /eureka/apps/CLIENT$index $status")
                                    }
                                    .subscribe { body ->
                                        logger.warn("POST /eureka/apps/CLIENT$index $status: $body")
                                        countDown.countDown()
                                    }
                        }
                    }
        }

        Flux.interval(Duration.ofSeconds(1), Duration.ofSeconds(10))
                .doOnEach({
                    (1..NUM_CLIENTS).forEach { index ->
                        client.put()
                                .uri { builder ->
                                    builder.path("/eureka/apps/{clientName}/{clientIp}")
                                            .queryParam("status", "UP")
                                            .build("CLIENT$index", CLIENT_IP)
                                }
                                .header("DiscoveryIdentity-Name", "DefaultClient")
                                .header("DiscoveryIdentity-Version", "1.4")
                                .header("DiscovertIdentity-Id", "10.200.10.1")
                                .exchange().subscribe {
                                    val status = it.statusCode().value()
                                    meterRegistry.counter("eureka.requests",
                                            "uri", "/eureka/apps/{clientName}/{clientIp}",
                                            "status", status.toString()).increment()
                                    if (status < 300) {
                                        logger.info("PUT /eureka/apps/CLIENT$index/$CLIENT_IP $status")
                                    } else {
                                        val countDown = CountDownLatch(1)
                                        it.bodyToMono<String>()
                                                .doOnTerminate {
                                                    if (countDown.count > 0)
                                                        logger.warn("PUT /eureka/apps/CLIENT$index/$CLIENT_IP $status")
                                                }
                                                .subscribe { body ->
                                                    logger.warn("PUT /eureka/apps/CLIENT$index/$CLIENT_IP $status: $body")
                                                    countDown.countDown()
                                                }
                                    }

                                }
                    }
                })
                .blockLast()
    }
}