package load

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import java.time.Duration

/**
 * @author Jon Schneider
 */

object EurekaGets {
    val logger = LoggerFactory.getLogger(EurekaGets::class.java)

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

        Flux.interval(Duration.ofMillis(100)).doOnEach {
            client.get()
                    .uri("/eureka/apps")
                    .exchange()
                    .subscribe {
                        val status = it.statusCode().value()
                        logger.debug("GET /eureka/apps $status")
                        meterRegistry.counter("eureka.requests", "uri", "/eureka/apps",
                                "status", status.toString()).increment()

                        it.bodyToMono<String>().subscribe { applications ->
                            meterRegistry.summary("applications.size", "uri", "/eureka/apps")
                                    .record((applications.length * 2).toDouble())
                        }
                    }
        }.blockLast()
    }
}