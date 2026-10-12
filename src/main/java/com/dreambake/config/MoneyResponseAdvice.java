package com.dreambake.config;

import com.dreambake.controller.InventoryRestController;
import com.dreambake.controller.OrderRestController;
import com.dreambake.controller.ProductRestController;
import com.dreambake.controller.RecipeRestController;
import com.dreambake.util.MoneyVisibility;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Los montos del negocio solo los ve el ADMIN (paso 9.3b).
 *
 * Antes, el panel del pastelero escondía los montos solo con CSS: el servidor igual los mandaba y
 * cualquiera podía verlos abriendo las herramientas del navegador. Ahora, cuando la sesión es de un
 * PASTELERO (sin ADMIN), esta clase saca los campos de dinero de la respuesta JSON ANTES de enviarla.
 *
 * Cómo funciona, en simple:
 *  1. Spring arma la respuesta del controlador como siempre (con las entidades).
 *  2. Si la persona es pastelero, la respuesta se copia a un árbol JSON y a esa COPIA se le quitan los
 *     campos de MoneyVisibility.MONEY_FIELDS. Las entidades de Hibernate no se tocan, así que nada
 *     de esto puede guardarse por error en la base.
 *  3. Para el admin (y para la tienda pública) no hace nada.
 *
 * Solo aplica a los controladores que el pastelero puede usar. Bakey llama a los métodos de los
 * controladores directamente (no pasa por acá) y tiene su propia regla de privacidad (BakeyPrivacy).
 */
@RestControllerAdvice(assignableTypes = {
        OrderRestController.class, InventoryRestController.class, RecipeRestController.class,
        ProductRestController.class })
public class MoneyResponseAdvice implements ResponseBodyAdvice<Object> {

    private final JsonMapper jsonMapper;

    public MoneyResponseAdvice(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof CharSequence || !MoneyVisibility.hiddenForCurrentUser()) {
            return body;
        }
        JsonNode copy = jsonMapper.valueToTree(body);
        strip(copy);
        return copy;
    }

    // Recorre el árbol (objetos y listas, a cualquier profundidad) y borra los campos de dinero
    private void strip(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String field : MoneyVisibility.MONEY_FIELDS) {
                object.remove(field);
            }
        }
        for (JsonNode child : node) {   // los valores de un objeto o los elementos de una lista
            strip(child);
        }
    }
}
