package gov.assets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.http.MediaType;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1","tracking.admin-password=test-admin-password","tracking.officer-password=test-officer-password"})
@AutoConfigureMockMvc
@Transactional
class ApplicationTest {
    @Autowired AssetRepository assets;
    @Autowired AlertRepository alerts;
    @Autowired LogRepository logs;
    @Autowired TrackingService tracking;
    @Autowired MockMvc mvc;
    Asset asset(){Asset a=new Asset();a.name="Test phone";a.type="Phone";a.serialNo="TEST-1";a.bluetoothMac="AA:BB:CC:DD:EE:FF";a.department="IT";a.assignedTo="Officer";return assets.saveAndFlush(a);}
    @Test void transitionsAreIdempotentAndReconnectResolves(){
        Asset a=asset();tracking.transition(a.id,Asset.Status.CONNECTED,"Test");
        tracking.transition(a.id,Asset.Status.DISCONNECTED,"Test");tracking.transition(a.id,Asset.Status.DISCONNECTED,"Test");
        assertThat(alerts.countByResolvedFalse()).isEqualTo(1);assertThat(logs.count()).isEqualTo(2);
        tracking.transition(a.id,Asset.Status.CONNECTED,"Test");
        assertThat(alerts.countByResolvedFalse()).isZero();assertThat(logs.count()).isEqualTo(3);
        assertThat(alerts.findAll().get(0).resolvedAt).isNotNull();
    }
    @Test void deletionRetainsAuditAndClosesAlerts(){Asset a=asset();tracking.transition(a.id,Asset.Status.DISCONNECTED,"Test");tracking.delete(a.id);assertThat(assets.existsById(a.id)).isFalse();assertThat(logs.count()).isEqualTo(1);assertThat(alerts.countByResolvedFalse()).isZero();}
    static final String INPUT="{\"name\":\"Phone\",\"type\":\"Phone\",\"serialNo\":\"X\",\"bluetoothMac\":\"AA:BB:CC:DD:EE:01\",\"department\":\"IT\",\"assignedTo\":\"Officer\"}";
    @Test void unauthenticatedApiReturns401() throws Exception {mvc.perform(get("/api/summary")).andExpect(status().isUnauthorized());}
    @Test void reactDeepLinksServeEntryPoint() throws Exception {mvc.perform(get("/reports")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));mvc.perform(get("/assets/1/edit")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));}
    @Test void sessionProvidesAnonymousCsrfToken() throws Exception {mvc.perform(get("/api/session")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.csrfToken").isNotEmpty());}
    @Test @WithMockUser(roles="OFFICER") void officersCannotMutateAssets() throws Exception {mvc.perform(delete("/api/assets/1").with(csrf())).andExpect(status().isForbidden());mvc.perform(post("/api/assets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT)).andExpect(status().isForbidden());}
    @Test @WithMockUser(roles="OFFICER") void officersCanReadData() throws Exception {mvc.perform(get("/api/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("demo"));mvc.perform(get("/api/logs")).andExpect(status().isOk());}
    @Test @WithMockUser(roles="ADMIN") void csrfRequired() throws Exception {mvc.perform(delete("/api/assets/1")).andExpect(status().isForbidden());}
    @Test @WithMockUser(roles="ADMIN") void invalidMacRejected() throws Exception {mvc.perform(post("/api/assets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT.replace("AA:BB:CC:DD:EE:01","bad"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.bluetoothMac").exists());assertThat(assets.count()).isZero();}
    @Test @WithMockUser(roles="ADMIN") void clientCannotOverrideStatus() throws Exception {mvc.perform(post("/api/assets").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(INPUT.replace("\"name\"", "\"status\":\"CONNECTED\",\"name\""))).andExpect(status().isCreated());assertThat(assets.findAll().get(0).status).isEqualTo(Asset.Status.UNKNOWN);}
    @Autowired BlockRepository blocks;
    @Autowired BlockchainService blockchain;
    @Test void locationShiftAndBlockchainVerification() {
        Asset a = asset();
        a.allowedLocations = "HQ Server Room, Warehouse A";
        a.currentLocation = "HQ Server Room";
        assets.saveAndFlush(a);

        // Authorized shift
        TrackingService.ShiftResult r1 = tracking.shiftLocation(a.id, "Warehouse A", "admin");
        assertThat(r1.authorized()).isTrue();
        assertThat(r1.alertCreated()).isFalse();
        assertThat(alerts.countByResolvedFalse()).isZero();

        // Unauthorized shift
        TrackingService.ShiftResult r2 = tracking.shiftLocation(a.id, "External Zone", "admin");
        assertThat(r2.authorized()).isFalse();
        assertThat(r2.alertCreated()).isTrue();
        assertThat(alerts.countByResolvedFalse()).isEqualTo(1);
        assertThat(alerts.findAll().get(0).message).contains("Unauthorized asset movement");

        // Chain integrity check
        BlockchainService.ChainVerificationResult verify = blockchain.verifyChain();
        assertThat(verify.valid()).isTrue();
        assertThat(verify.totalBlocks()).isGreaterThanOrEqualTo(3);
    }
    @Test void simulationDeepLinksServeEntryPoint() throws Exception {
        mvc.perform(get("/simulation")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
}
