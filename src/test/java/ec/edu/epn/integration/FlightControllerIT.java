package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.AirportRequest;
import ec.edu.epn.dto.FlightRequest;
import ec.edu.epn.model.Airport;
import ec.edu.epn.model.Flight;
import ec.edu.epn.repository.AirportRepository;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class FlightControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlightRepository flightRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    // IDs de aeropuertos creados en @BeforeEach, reutilizados en cada prueba
    private long originId;
    private long destinationId;

    // Tiempos de referencia
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 7, 15, 10, 0);
    private static final LocalDateTime ARRIVAL   = LocalDateTime.of(2026, 7, 15, 12, 30);

    @BeforeEach
    void setUp() throws Exception {
        // Limpiar en orden para respetar las FK
        flightRepository.deleteAll();
        airportRepository.deleteAll();

        originId      = createAirportViaApi("Mariscal Sucre", "UIO", "Quito",     "Ecuador");
        destinationId = createAirportViaApi("El Dorado",      "BOG", "Bogotá",    "Colombia");
    }

    // -----------------------------------------------------------------------
    // POST /api/flights
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldCreateFlight — HTTP 201 con datos correctos")
    void shouldCreateFlight() throws Exception {
        FlightRequest request = buildFlightRequest("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.flightNumber").value("AV100"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.origin.code").value("UIO"))
                .andExpect(jsonPath("$.destination.code").value("BOG"));
    }

    @Test
    @DisplayName("shouldRejectDuplicateFlightNumber — HTTP 400 al repetir número de vuelo")
    void shouldRejectDuplicateFlightNumber() throws Exception {
        createFlightViaApi("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        FlightRequest duplicate = buildFlightRequest("AV100", originId, destinationId,
                DEPARTURE.plusDays(1), ARRIVAL.plusDays(1), "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("shouldRejectArrivalBeforeDeparture — HTTP 400 cuando arrivalTime < departureTime")
    void shouldRejectArrivalBeforeDeparture() throws Exception {
        FlightRequest bad = buildFlightRequest(
                "AV200", originId, destinationId,
                DEPARTURE, DEPARTURE.minusHours(1),   // llegada antes de salida
                "SCHEDULED");

        mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // -----------------------------------------------------------------------
    // GET /api/flights
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindAllFlights — devuelve lista con todos los vuelos")
    void shouldFindAllFlights() throws Exception {
        createFlightViaApi("AV100", originId, destinationId, DEPARTURE,           ARRIVAL,           "SCHEDULED");
        createFlightViaApi("AV101", originId, destinationId, DEPARTURE.plusDays(1), ARRIVAL.plusDays(1), "SCHEDULED");

        mockMvc.perform(get("/api/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].flightNumber", containsInAnyOrder("AV100", "AV101")));
    }

    // -----------------------------------------------------------------------
    // GET /api/flights/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindFlightById — retorna el vuelo correcto")
    void shouldFindFlightById() throws Exception {
        long id = createFlightViaApi("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        mockMvc.perform(get("/api/flights/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.flightNumber").value("AV100"));
    }

    @Test
    @DisplayName("shouldReturn404WhenFlightNotFound — ID inexistente → HTTP 404")
    void shouldReturn404WhenFlightNotFound() throws Exception {
        mockMvc.perform(get("/api/flights/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // -----------------------------------------------------------------------
    // GET /api/flights/number/{flightNumber}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindFlightByFlightNumber — búsqueda por número de vuelo")
    void shouldFindFlightByFlightNumber() throws Exception {
        createFlightViaApi("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        mockMvc.perform(get("/api/flights/number/{flightNumber}", "AV100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("AV100"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    // -----------------------------------------------------------------------
    // GET /api/flights/status/{status}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindFlightsByStatus — filtra correctamente por estado")
    void shouldFindFlightsByStatus() throws Exception {
        createFlightViaApi("AV100", originId, destinationId, DEPARTURE,           ARRIVAL,           "SCHEDULED");
        createFlightViaApi("AV101", originId, destinationId, DEPARTURE.plusDays(1), ARRIVAL.plusDays(1), "DELAYED");

        mockMvc.perform(get("/api/flights/status/{status}", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].flightNumber").value("AV100"));
    }

    // -----------------------------------------------------------------------
    // GET /api/flights/between
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldFindFlightsBetweenDates — retorna vuelos en el rango indicado")
    void shouldFindFlightsBetweenDates() throws Exception {
        // Dentro del rango
        createFlightViaApi("AV100", originId, destinationId,
                LocalDateTime.of(2026, 7, 15, 10, 0),
                LocalDateTime.of(2026, 7, 15, 12, 30), "SCHEDULED");
        // Fuera del rango
        createFlightViaApi("AV200", originId, destinationId,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 13, 0), "SCHEDULED");

        String start = "2026-07-01T00:00:00";
        String end   = "2026-07-31T23:59:59";

        mockMvc.perform(get("/api/flights/between")
                .param("start", start)
                .param("end", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].flightNumber").value("AV100"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/flights/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldUpdateFlight — actualiza estado del vuelo correctamente")
    void shouldUpdateFlight() throws Exception {
        long id = createFlightViaApi("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        FlightRequest updated = buildFlightRequest("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "IN_FLIGHT");

        mockMvc.perform(put("/api/flights/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_FLIGHT"))
                .andExpect(jsonPath("$.flightNumber").value("AV100"));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/flights/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("shouldDeleteFlight — elimina y verifica HTTP 404 posterior")
    void shouldDeleteFlight() throws Exception {
        long id = createFlightViaApi("AV100", originId, destinationId, DEPARTURE, ARRIVAL, "SCHEDULED");

        mockMvc.perform(delete("/api/flights/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/flights/{id}", id))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Helpers privados
    // -----------------------------------------------------------------------

    private FlightRequest buildFlightRequest(String number, long origin, long destination,
                                              LocalDateTime dep, LocalDateTime arr, String status) {
        FlightRequest r = new FlightRequest();
        r.setFlightNumber(number);
        r.setOriginId(origin);
        r.setDestinationId(destination);
        r.setDepartureTime(dep);
        r.setArrivalTime(arr);
        r.setStatus(status);
        return r;
    }

    /**
     * Crea un vuelo via POST y devuelve su ID generado.
     */
    private long createFlightViaApi(String number, long origin, long destination,
                                     LocalDateTime dep, LocalDateTime arr, String status) throws Exception {
        FlightRequest request = buildFlightRequest(number, origin, destination, dep, arr, status);

        MvcResult result = mockMvc.perform(post("/api/flights")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Flight created = objectMapper.readValue(result.getResponse().getContentAsString(), Flight.class);
        return created.getId();
    }

    /**
     * Crea un aeropuerto via POST y devuelve su ID generado.
     */
    private long createAirportViaApi(String name, String code, String city, String country) throws Exception {
        AirportRequest request = new AirportRequest();
        request.setName(name);
        request.setCode(code);
        request.setCity(city);
        request.setCountry(country);

        MvcResult result = mockMvc.perform(post("/api/airports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Airport created = objectMapper.readValue(result.getResponse().getContentAsString(), Airport.class);
        return created.getId();
    }
}