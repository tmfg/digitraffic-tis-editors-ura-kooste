package fi.digitraffic.tis.ura;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
class KoosteResourceTest {
    @Test
    void testHelloEndpoint() {
        given()
          .when().get("/hello/greeting")
          .then()
             .statusCode(200)
             .body(containsString("hello unnamed wanderer"));
    }

}
