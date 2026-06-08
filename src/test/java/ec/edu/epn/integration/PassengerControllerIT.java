package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.PassengerRequest;
import ec.edu.epn.model.Passenger;
import ec.edu.epn.repository.FlightRepository;
import ec.edu.epn.repository.PassengerRepository;
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
class PassengerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private FlightRepository flightRepository;

    @BeforeEach
    void setUp() {
        // Los vuelos referencian pasajeros vía ManyToMany; limpiar primero los vuelos
        flightRepository.deleteAll();
        passengerRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // POST /api/passengers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldCreatePassenger — HTTP 201 con datos correctos")
    void shouldCreatePassenger() throws Exception {
        PassengerRequest request = buildRequest("Ana", "García", "ana.garcia@epn.edu.ec", "P001234");

        mockMvc.perform(post("/api/passengers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("García"))
                .andExpect(jsonPath("$.email").value("ana.garcia@epn.edu.ec"))
                .andExpect(jsonPath("$.passportNumber").value("P001234"));
    }

    @Test
    @DisplayName("shouldRejectDuplicateEmail — HTTP 400 al repetir email")
    void shouldRejectDuplicateEmail() throws Exception {
        createPassengerViaApi("Ana", "García", "ana.garcia@epn.edu.ec", "P001234");

        PassengerRequest duplicate = buildRequest("Luis", "Torres", "ana.garcia@epn.edu.ec", "P005678");

        mockMvc.perform(post("/api/passengers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("shouldRejectInvalidEmail — HTTP 400 con email mal formado")
    void shouldRejectInvalidEmail() throws Exception {
        PassengerRequest invalid = buildRequest("Pedro", "Ruiz", "esto-no-es-un-email", "P009999");

        mockMvc.perform(post("/api/passengers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]", containsString("email")));
    }

    @Test
    @DisplayName("shouldRejectBlankFields — HTTP 400 cuando los campos obligatorios están vacíos")
    void shouldRejectBlankFields() throws Exception {
        PassengerRequest empty = new PassengerRequest();

        mockMvc.perform(post("/api/passengers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // -----------------------------------------------------------------------
    // GET /api/passengers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindAllPassengers — devuelve todos los pasajeros registrados")
    void shouldFindAllPassengers() throws Exception {
        createPassengerViaApi("Ana",   "García", "ana@epn.edu.ec",  "P001");
        createPassengerViaApi("Luis",  "Torres", "luis@epn.edu.ec", "P002");

        mockMvc.perform(get("/api/passengers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].passportNumber", containsInAnyOrder("P001", "P002")));
    }

    // -----------------------------------------------------------------------
    // GET /api/passengers/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindPassengerById — retorna el pasajero correcto")
    void shouldFindPassengerById() throws Exception {
        long id = createPassengerViaApi("Ana", "García", "ana@epn.edu.ec", "P001234");

        mockMvc.perform(get("/api/passengers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@epn.edu.ec"));
    }

    @Test
    @DisplayName("shouldReturn404WhenPassengerNotFound — ID inexistente → HTTP 404")
    void shouldReturn404WhenPassengerNotFound() throws Exception {
        mockMvc.perform(get("/api/passengers/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // -----------------------------------------------------------------------
    // GET /api/passengers/email/{email}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindPassengerByEmail — búsqueda por email")
    void shouldFindPassengerByEmail() throws Exception {
        createPassengerViaApi("Ana", "García", "ana@epn.edu.ec", "P001234");

        mockMvc.perform(get("/api/passengers/email/{email}", "ana@epn.edu.ec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@epn.edu.ec"))
                .andExpect(jsonPath("$.firstName").value("Ana"));
    }

    // -----------------------------------------------------------------------
    // GET /api/passengers/passport/{passportNumber}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindPassengerByPassportNumber — búsqueda por número de pasaporte")
    void shouldFindPassengerByPassportNumber() throws Exception {
        createPassengerViaApi("Ana", "García", "ana@epn.edu.ec", "P001234");

        mockMvc.perform(get("/api/passengers/passport/{passport}", "P001234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportNumber").value("P001234"))
                .andExpect(jsonPath("$.lastName").value("García"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/passengers/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldUpdatePassenger — actualiza datos del pasajero correctamente")
    void shouldUpdatePassenger() throws Exception {
        long id = createPassengerViaApi("Ana", "García", "ana@epn.edu.ec", "P001234");

        PassengerRequest updated = buildRequest("Ana María", "García López", "ana.m@epn.edu.ec", "P001234");

        mockMvc.perform(put("/api/passengers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana María"))
                .andExpect(jsonPath("$.lastName").value("García López"))
                .andExpect(jsonPath("$.email").value("ana.m@epn.edu.ec"));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/passengers/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldDeletePassenger — elimina y verifica HTTP 404 posterior")
    void shouldDeletePassenger() throws Exception {
        long id = createPassengerViaApi("Ana", "García", "ana@epn.edu.ec", "P001234");

        mockMvc.perform(delete("/api/passengers/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/passengers/{id}", id))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helpers privados
    // -----------------------------------------------------------------------

    private PassengerRequest buildRequest(String firstName, String lastName,
                                           String email, String passport) {
        PassengerRequest r = new PassengerRequest();
        r.setFirstName(firstName);
        r.setLastName(lastName);
        r.setEmail(email);
        r.setPassportNumber(passport);
        return r;
    }

    /**
     * Crea un pasajero via POST y devuelve su ID generado.
     */
    private long createPassengerViaApi(String firstName, String lastName,
                                        String email, String passport) throws Exception {
        PassengerRequest request = buildRequest(firstName, lastName, email, passport);

        MvcResult result = mockMvc.perform(post("/api/passengers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Passenger created = objectMapper.readValue(result.getResponse().getContentAsString(), Passenger.class);
        return created.getId();
    }
}