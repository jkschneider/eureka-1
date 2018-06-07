package load

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.CountDownLatch

object EurekaBatchReplications {
    private const val NUM_REPLICAS = 10
    private val REPLICATION_INTERVAL_SECONDS = Duration.ofSeconds(10)

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

        val body = {
            val clients = (1..NUM_CLIENTS).map { n ->
                """{"appName":"CLIENT$n","id":"10.20.100.1","lastDirtyTimestamp":${System.currentTimeMillis()},"status":"UP","action":"Heartbeat"}"""
            }.joinToString(",")
            """{"replicationList":[$clients]}"""
        }

        Flux.interval(Duration.ofSeconds(3), REPLICATION_INTERVAL_SECONDS)
                .doOnEach({
                    (1..NUM_REPLICAS).forEach { index ->
                        client.post()
                                .uri("/eureka/peerreplication/batch/")
                                .body(BodyInserters.fromObject(body()))
                                .header("DiscoveryIdentity-Name", "DefaultClient")
                                .header("DiscoveryIdentity-Version", "1.4")
                                .header("DiscovertIdentity-Id", "10.200.10.1")
                                .exchange().subscribe {
                                    val status = it.statusCode().value()
                                    meterRegistry.counter("eureka.requests",
                                            "uri", "/eureka/peerreplication/batch/",
                                            "status", status.toString()).increment()
                                    if (status < 300) {
                                        logger.debug("POST /eureka/peerreplication/batch/ $status")
                                    } else {
                                        val countDown = CountDownLatch(1)
                                        it.bodyToMono<String>()
                                                .doOnTerminate {
                                                    if (countDown.count > 0)
                                                        logger.warn("POST /eureka/peerreplication/batch/ $status")
                                                }
                                                .subscribe { body ->
                                                    logger.warn("POST /eureka/peerreplication/batch/ $status: $body")
                                                    countDown.countDown()
                                                }
                                    }
                                }
                    }
                })
                .blockLast()
    }
}