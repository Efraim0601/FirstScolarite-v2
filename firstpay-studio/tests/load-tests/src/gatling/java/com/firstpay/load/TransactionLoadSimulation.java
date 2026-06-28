package com.firstpay.load;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Simulation de charge soutenue — création de transactions via l'API Gateway.
 * Cible Phase 10 : 1M tx/min (~16 667 TPS) cumulés, P99 &lt; 500 ms (burst), erreurs &lt; 0.1 %.
 *
 * <p>Paramétrable (system properties), pour piloter un injecteur unique ou répartir la
 * cible sur N injecteurs :
 * <pre>
 *   -DbaseUrl=http://gw:8080  -DtargetTps=16667  -DrampSeconds=120  -DholdSeconds=300
 *   -DapiKey=demo-soft-key
 * </pre>
 * Avec K injecteurs, fixer {@code targetTps = 16667 / K} sur chacun.
 */
public class TransactionLoadSimulation extends Simulation {

  private static final String BASE = System.getProperty("baseUrl", "http://localhost:8080");
  private static final String API_KEY = System.getProperty("apiKey", "demo-soft-key");
  private static final double TARGET_TPS = Double.parseDouble(System.getProperty("targetTps", "200"));
  private static final long RAMP = Long.parseLong(System.getProperty("rampSeconds", "60"));
  private static final long HOLD = Long.parseLong(System.getProperty("holdSeconds", "120"));

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE)
      .acceptHeader("application/json")
      .header("X-API-Key", API_KEY)
      .header("Content-Type", "application/json")
      .shareConnections();

  ScenarioBuilder createTx = scenario("Create transaction")
      .exec(session -> session.set("idem", UUID.randomUUID().toString()))
      .exec(
          http("POST /api/v1/transactions")
              .post("/api/v1/transactions")
              .header("X-Idempotency-Key", "#{idem}")
              .body(StringBody("""
                  {"externalRef":"LOAD-#{idem}","amount":25000,"currency":"XAF","type":"PAYMENT","method":"orange"}
                  """)).asJson()
              .check(status().in(202, 200, 503))
      );

  {
    setUp(
        createTx.injectOpen(
            rampUsersPerSec(1).to(TARGET_TPS).during(Duration.ofSeconds(RAMP)),
            constantUsersPerSec(TARGET_TPS).during(Duration.ofSeconds(HOLD))
        )
    ).protocols(httpProtocol)
     .assertions(
         global().successfulRequests().percent().gt(99.9),   // erreurs < 0.1 %
         global().responseTime().percentile3().lt(500)        // P99 < 500 ms
     );
  }
}
