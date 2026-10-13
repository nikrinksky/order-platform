package com.orderplatform.product.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.product.AbstractProductIntegrationTest;
import com.orderplatform.product.model.Product;
import com.orderplatform.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для Product Controller.
 */
@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends AbstractProductIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    private Product product(String id, String name, boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Description of " + name)
                .price(new BigDecimal("99.99"))
                .category("Electronics")
                .quantity(10)
                .active(active)
                .build();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        String productJson = """
                {
                    "name": "Test Product",
                    "description": "Test Description",
                    "price": 99.99,
                    "category": "Electronics",
                    "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Product"))
                // The price must be a JSON number, not a string: order-service multiplies it
                .andExpect(jsonPath("$.price").isNumber())
                .andExpect(jsonPath("$.price").value(99.99));
    }

    @Test
    void shouldRejectProductWithoutName() throws Exception {
        String productJson = """
                {
                    "name": "",
                    "description": "Test",
                    "price": 99.99,
                    "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPriceWithMoreThanTwoFractionDigits() throws Exception {
        String productJson = """
                {
                    "name": "Test",
                    "price": 10.999
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isBadRequest());
    }

    /**
     * The catalogue is public, so an unpublished product must look missing to a visitor.
     */
    @Test
    void shouldHideUnpublishedProductFromAnonymousVisitor() throws Exception {
        productRepository.save(product("hidden-1", "Draft Product", false));

        mockMvc.perform(get("/api/products/hidden-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotListUnpublishedProductsToAnonymousVisitor() throws Exception {
        productRepository.save(product("published-1", "Published", true));
        productRepository.save(product("hidden-1", "Draft", false));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("published-1"));
    }

    @Test
    void shouldExcludeUnpublishedProductsFromSearchForVisitor() throws Exception {
        productRepository.save(product("published-1", "Phone", true));
        productRepository.save(product("hidden-1", "Phone Draft", false));

        mockMvc.perform(get("/api/products/search").param("text", "Phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("published-1"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldShowUnpublishedProductToAdministrator() throws Exception {
        productRepository.save(product("hidden-1", "Draft Product", false));

        mockMvc.perform(get("/api/products/hidden-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * The search text is a literal, not a regular expression: ".*" must not match everything.
     */
    @Test
    void shouldTreatSearchTextAsLiteralNotAsRegex() throws Exception {
        productRepository.save(product("published-1", "Phone", true));

        mockMvc.perform(get("/api/products/search").param("text", ".*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldNotFailSearchOnRegexMetacharacters() throws Exception {
        productRepository.save(product("published-1", "Phone", true));

        // An unescaped "[" is an invalid regular expression and used to break the query
        mockMvc.perform(get("/api/products/search").param("text", "["))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
