package load

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Duration

object EurekaRegistrations {
    //    const val EUREKA_HOST = "http://localhost:8761"
    const val EUREKA_HOST = "https://citi-82591.cfapps.io"
    const val CLIENT_IP = "10.20.100.1"

    val logger = LoggerFactory.getLogger(EurekaLoad::class.java)

    val meterRegistry = SimpleMeterRegistry()

    @JvmStatic
    fun main(args: Array<String>) {
        val client = WebClient
                .builder()
                .baseUrl(EUREKA_HOST)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .build()

        (0..500).forEach { index ->
            val timestamp = System.currentTimeMillis()

            val body = """
                        {
                          "instance": {
                            "instanceId": "$CLIENT_IP",
                            "hostName": "$CLIENT_IP",
                            "app": "CLIENT-$index",
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
                    .uri("/eureka/apps/{clientName}", "CLIENT-$index")
                    .body(BodyInserters.fromObject(body))
                    .exchange().subscribe {
                        val status = it.statusCode().value()
                        meterRegistry.counter("eureka.requests", "uri", "/eureka/apps/{clientName}",
                                "status", status.toString()).increment()
                        if (status < 300) {
                            logger.debug("POST /eureka/apps/CLIENT-$index $status")
                        } else {
                            it.bodyToMono<String>().subscribe { body ->
                                logger.warn("POST /eureka/apps/CLIENT-$index $status: $body")
                            }
                        }
                    }
        }

        Flux.interval(Duration.ofSeconds(10))
                .doOnEach({ n ->
                    (0..500).forEach { index ->
                        client.put()
                                .uri { builder ->
                                    builder.path("/eureka/apps/{clientName}/{clientIp}")
                                            .queryParam("status", "UP")
                                            .queryParam("lastDirtyTimestamp", EurekaLoad.clients[index])
                                            .build("CLIENT-$index", EurekaLoad.CLIENT_IP)
                                }
                                .exchange().subscribe {
                                    val status = it.statusCode().value()
                                    EurekaLoad.meterRegistry.counter("eureka.requests",
                                            "uri", "/eureka/apps/{clientName}/{clientIp}",
                                            "status", status.toString()).increment()
                                    if (status < 300) {
                                        EurekaLoad.logger.debug("UT /eureka/apps/CLIENT-$index/${EurekaLoad.CLIENT_IP} $status")
                                    } else {
                                        it.bodyToMono<String>().subscribe { body ->
                                            EurekaLoad.logger.warn("UT /eureka/apps/CLIENT-$index/${EurekaLoad.CLIENT_IP} $status: $body")
                                        }
                                    }

                                }
                    }
                })
                .blockLast()
    }
}