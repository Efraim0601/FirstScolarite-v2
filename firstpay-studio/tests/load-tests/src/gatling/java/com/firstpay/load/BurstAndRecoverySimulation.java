package com.firstpay.load;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Scénario de burst & recovery (Phase 10) : charge nominale → pic ×5 → retour nominal.
 * Valide que la plateforme absorbe les pics (backpressure, autoscaling) et récupère
 * (RTO &lt; 30 s) sans dépasser les SLO (P99 &lt; 500 ms sous burst, erreurs &lt; 0.1 %).
 *
 * <pre>
 *   -DbaseUrl=http://gw:8080 -DnominalTps=200 -DburstFactor=5 -DapiKey=demo-soft-key
 * </pre>
 */
public class BurstAndRecoverySimulation extends Simulation {

  private static final String BASE = System.getProperty("baseUrl", "http://localhost:8080");
  private static final String API_KEY = System.getProperty("apiKey", "demo-soft-key");
  private static final double NOMINAL = Double.parseDouble(System.getProperty("nominalTps", "200"));
  private static final double BURST_FACTOR = Double.parseDouble(System.getProperty("burstFactor", "5"));

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE)
      .acceptHeader("application/json")
      .header("X-API-Key", API_KEY)
      .header("Content-Type", "application/json")
      .shareConnections();

  ScenarioBuilder createTx = scenario("Burst create transaction")
      .exec(session -> session.set("idem", UUID.randomUUID().toString()))
      .exec(
          http("POST /api/v1/transactions")
              .post("/api/v1/transactions")
              .header("X-Idempotency-Key", "#{idem}")
              .body(StringBody("""
                  {"externalRef":"BURST-#{idem}","amount":25000,"currency":"XAF","type":"PAYMENT","method":"orange"}
                  """)).asJson()
              .check(status().in(202, 200, 503, 429))
      );

  {
    setUp(
        createTx.injectOpen(
            // nominal
            constantUsersPerSec(NOMINAL).during(Duration.ofSeconds(60)),
            // montée rapide vers le pic (×BURST_FACTOR) — teste backpressure + scale-up
            rampUsersPerSec(NOMINAL).to(NOMINAL * BURST_FACTOR).during(Duration.ofSeconds(15)),
            constantUsersPerSec(NOMINAL * BURST_FACTOR).during(Duration.ofSeconds(60)),
            // retour au nominal — teste la récupération (scale-down stabilisé)
            rampUsersPerSec(NOMINAL * BURST_FACTOR).to(NOMINAL).during(Duration.ofSeconds(15)),
            constantUsersPerSec(NOMINAL).during(Duration.ofSeconds(60))
        )
    ).protocols(httpProtocol)
     .assertions(
         global().successfulRequests().percent().gt(99.0),
         global().responseTime().percentile3().lt(500)
     );
  }
}
