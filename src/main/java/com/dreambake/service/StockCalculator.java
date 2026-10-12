package com.dreambake.service;

import com.dreambake.entity.InventoryItem;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderItem;
import com.dreambake.entity.Product;
import com.dreambake.entity.RecipeItem;
import com.dreambake.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Cálculo de insumos de los pedidos según las recetas (paso 5.3).
 *
 * Antes vivía dentro de OrderRestController (getRequiredStock); se pasó a este servicio, sin cambiar
 * cómo calcula, para que también lo use Bakey ("¿qué tengo que comprar para los pedidos del finde?").
 *
 * La cadena es: Pedido → Ítem → Producto → Receta → Ítem de receta → Insumo.
 * Cada OrderItem es UNA unidad (si piden 3 alfajores hay 3 ítems), así que se suma la receta una vez por ítem.
 * Las tortas del creador y los pedidos escritos a mano no tienen receta: no suman insumos.
 */
@Service
public class StockCalculator {

    private final ProductRepository productRepository;

    public StockCalculator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Insumos que necesita un pedido: id del insumo → cantidad (en la unidad del insumo). */
    public Map<Long, Double> requiredFor(Order order) {
        Map<Long, Double> required = new HashMap<>();
        if (order.getItems() == null) return required;
        for (OrderItem item : order.getItems()) {
            Product product = productOf(item);
            if (product != null && product.getRecipe() != null && product.getRecipe().getItems() != null) {
                for (RecipeItem recipeItem : product.getRecipe().getItems()) {
                    if (recipeItem.getInventoryItem() != null && recipeItem.getInventoryItem().getId() != null) {
                        required.merge(recipeItem.getInventoryItem().getId(), recipeItem.getQuantityRequired(), Double::sum);
                    }
                }
            }
        }
        return required;
    }

    /** Lo mismo para varios pedidos juntos (se suman). */
    public Map<Long, Double> requiredFor(Collection<Order> orders) {
        Map<Long, Double> total = new HashMap<>();
        for (Order order : orders) {
            requiredFor(order).forEach((id, qty) -> total.merge(id, qty, Double::sum));
        }
        return total;
    }

    /** Cuántos ítems del pedido no tienen receta (no se puede calcular qué insumos llevan). */
    public int itemsWithoutRecipe(Order order) {
        if (order.getItems() == null) return 0;
        int count = 0;
        for (OrderItem item : order.getItems()) {
            Product product = productOf(item);
            if (product == null || product.getRecipe() == null
                    || product.getRecipe().getItems() == null || product.getRecipe().getItems().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Stock que se puede usar: si el insumo está vencido, cuenta como 0. */
    public double usableStock(InventoryItem item) {
        boolean expired = item.getExpirationDate() != null && item.getExpirationDate().isBefore(LocalDate.now());
        return (item.getCurrentStock() != null && !expired) ? item.getCurrentStock() : 0.0;
    }

    // El producto del ítem; los carritos viejos solo guardaban el nombre, así que se busca por nombre
    private Product productOf(OrderItem item) {
        Product product = item.getProduct();
        if (product == null && item.getProductType() != null) {
            product = productRepository.findByNameIgnoreCase(item.getProductType()).orElse(null);
        }
        return product;
    }
}
