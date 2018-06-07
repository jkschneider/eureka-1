package load

import java.time.Duration

const val EUREKA_HOST = "http://localhost:8761"
//const val EUREKA_HOST = "https://citi-82591.cfapps.io"

/**
 * Controls the frequency of heartbeats coming from the heartbeats load test
 */
val HEARTBEAT_INTERVAL_SECONDS: Duration = Duration.ofSeconds(10)

/**
 * The number of simulated clients registering with and sending heartbeats to Eureka.
 */
const val NUM_CLIENTS = 100

/**
 * Controls the frequency of batch replication requests
 */
val REPLICATION_INTERVAL_SECONDS: Duration = Duration.ofSeconds(10)

/**
 * The number of simulated replicas
 */
const val NUM_REPLICAS = 10

/**
 * The CLIENT_IP that each simulated "client" will report to Eureka.
 */
const val CLIENT_IP = "10.20.100.1"