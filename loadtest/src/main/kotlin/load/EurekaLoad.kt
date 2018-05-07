package load

import org.pcollections.HashTreePMap
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Signal
import java.time.Duration

/**
 * @author Jon Schneider
 */

object EurekaLoad {
    @Volatile
    var clients = HashTreePMap.empty<Int, Long>()

    val logger = LoggerFactory.getLogger(EurekaLoad::class.java)

    val meterRegistry = Prometheus.setup()

    @JvmStatic
    fun main(args: Array<String>) {
        val client = WebClient
                .builder()
                .baseUrl(EUREKA_HOST)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .build()

        Flux.interval(Duration.ofSeconds(30)).doOnEach { n: Signal<Long> ->
            val index: Int = n.get()!!.toInt()
            logger.info("Sending $index requests")

            (0..100).forEach {
                if (!clients.contains(index)) {
                    val timestamp = System.currentTimeMillis()
                    clients = clients.plus(index, timestamp)

                    client.get()
                            .uri("/eureka/apps")
                            .exchange()
                            .subscribe {
                                val status = it.statusCode().value()
                                logger.debug("GET /eureka/apps $status")
                                meterRegistry.counter("eureka.requests", "uri", "/eureka/apps",
                                        "status", status.toString()).increment()

                                it.bodyToMono<String>().subscribe { applications ->
                                    EurekaGets.meterRegistry.summary("applications.size", "uri", "/eureka/apps")
                                            .record((applications.length * 2).toDouble())
                                }
                            }

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

                    clients = clients.plus(index, timestamp)

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
                } else {
                    client.get()
                            .uri("/eureka/apps/delta")
                            .exchange()
                            .subscribe {
                                val status = it.statusCode().value()
                                logger.debug("GET /eureka/apps/delta $status")
                                meterRegistry.counter("eureka.requests", "uri", "/eureka/apps/delta",
                                        "status", status.toString()).increment()

                                it.bodyToFlux<String>().subscribe { applications ->
                                    meterRegistry.summary("applications.size", "uri", "/eureka/apps/delta")
                                            .record((applications.length * 2).toDouble())
                                }
                            }

                    client.put()
                            .uri { builder ->
                                builder.path("/eureka/apps/{clientName}/{clientIp}")
                                        .queryParam("status", "UP")
                                        .queryParam("lastDirtyTimestamp", clients[index])
                                        .build("CLIENT-$index", CLIENT_IP)
                            }
                            .exchange().subscribe {
                                val status = it.statusCode().value()
                                meterRegistry.counter("eureka.requests",
                                        "uri", "/eureka/apps/{clientName}/{clientIp}",
                                        "status", status.toString()).increment()
                                if (status < 300) {
                                    logger.debug("UT /eureka/apps/CLIENT-$index/$CLIENT_IP $status")
                                } else {
                                    it.bodyToMono<String>().subscribe { body ->
                                        logger.warn("UT /eureka/apps/CLIENT-$index/$CLIENT_IP $status: $body")
                                    }
                                }

                            }
                }
            }
        }.blockLast()
    }
}