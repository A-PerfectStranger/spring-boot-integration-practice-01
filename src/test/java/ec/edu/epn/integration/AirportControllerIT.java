package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import ec.edu.epn.model.Airport;
import ec.edu.epn.repository.AirportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AirportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AirportRepository airportRepository;

    @BeforeEach
    void setUp() {
        airportRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // POST /api/airports
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldCreateAirport — HTTP 201 con datos correctos")
    void shouldCreateAirport() throws Exception {
        AirportRequest request = buildRequest("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mariscal Sucre"))
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"))
                .andExpect(jsonPath("$.country").value("Ecuador"));
    }

    @Test
    @DisplayName("shouldRejectDuplicateAirportCode — HTTP 400 al repetir código IATA")
    void shouldRejectDuplicateAirportCode() throws Exception {
        createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        AirportRequest duplicate = buildRequest("Otro Aeropuerto", "UIO", "Latacunga", "Ecuador");

        mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("shouldRejectInvalidAirportRequest — HTTP 400 con datos vacíos")
    void shouldRejectInvalidAirportRequest() throws Exception {
        AirportRequest invalid = new AirportRequest();
        // todos los campos nulos → viola @NotBlank

        mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("shouldRejectInvalidAirportCode — HTTP 400 cuando el código no tiene 3 caracteres")
    void shouldRejectInvalidAirportCode() throws Exception {
        AirportRequest invalid = buildRequest("Aeropuerto Test", "UI", "Quito", "Ecuador");

        mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // -----------------------------------------------------------------------
    // GET /api/airports
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindAllAirports — devuelve lista con 2 aeropuertos")
    void shouldFindAllAirports() throws Exception {
        createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");
        createAirportViaApi("José Joaquín de Olmedo", "GYE", "Guayaquil", "Ecuador");

        mockMvc.perform(get("/api/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("UIO", "GYE")));
    }

    // -----------------------------------------------------------------------
    // GET /api/airports/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindAirportById — retorna el aeropuerto correcto")
    void shouldFindAirportById() throws Exception {
        long id = createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.city").value("Quito"));
    }

    @Test
    @DisplayName("shouldReturn404WhenAirportNotFound — ID inexistente → HTTP 404")
    void shouldReturn404WhenAirportNotFound() throws Exception {
        mockMvc.perform(get("/api/airports/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // -----------------------------------------------------------------------
    // GET /api/airports/code/{code}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindAirportByCode — búsqueda por código IATA")
    void shouldFindAirportByCode() throws Exception {
        createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(get("/api/airports/code/{code}", "UIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UIO"))
                .andExpect(jsonPath("$.name").value("Mariscal Sucre"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/airports/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldUpdateAirport — actualiza nombre y ciudad correctamente")
    void shouldUpdateAirport() throws Exception {
        long id = createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        AirportRequest updated = buildRequest("Aeropuerto Internacional Quito", "UIO", "Tababela", "Ecuador");

        mockMvc.perform(put("/api/airports/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aeropuerto Internacional Quito"))
                .andExpect(jsonPath("$.city").value("Tababela"))
                .andExpect(jsonPath("$.code").value("UIO"));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/airports/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldDeleteAirport — elimina y verifica HTTP 404 posterior")
    void shouldDeleteAirport() throws Exception {
        long id = createAirportViaApi("Mariscal Sucre", "UIO", "Quito", "Ecuador");

        mockMvc.perform(delete("/api/airports/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/airports/{id}", id))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helpers privados
    // -----------------------------------------------------------------------

    private AirportRequest buildRequest(String name, String code, String city, String country) {
        AirportRequest r = new AirportRequest();
        r.setName(name);
        r.setCode(code);
        r.setCity(city);
        r.setCountry(country);
        return r;
    }

    /**
     * Crea un aeropuerto via POST y devuelve su ID generado.
     */
    private long createAirportViaApi(String name, String code, String city, String country) throws Exception {
        AirportRequest request = buildRequest(name, code, city, country);

        MvcResult result = mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Airport created = objectMapper.readValue(result.getResponse().getContentAsString(), Airport.class);
        return created.getId();
    }
}