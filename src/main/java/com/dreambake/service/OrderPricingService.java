package com.dreambake.service;

import com.dreambake.entity.CakeSize;
import com.dreambake.entity.Coupon;
import com.dreambake.entity.CouponType;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderItem;
import com.dreambake.entity.Product;
import com.dreambake.repository.CakeSizeRepository;
import com.dreambake.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Calcula en el SERVIDOR el precio de un pedido de la tienda.
// Antes el total lo mandaba el navegador y alguien podía cambiarlo a mano (por ejemplo, $1).
// Ahora el navegador solo dice QUÉ compra (id del producto o del tamaño de torta) y acá se
// buscan los precios reales en la base, se aplica el cupón y se calcula el total.
@Service
public class OrderPricingService {

    private static final String CUSTOM_CAKE = "Torta Personalizada";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CakeSizeRepository cakeSizeRepository;

    @Autowired
    private CouponService couponService;

    // Pone el precio real a cada ítem, aplica el cupón y guarda el total en el pedido.
    // Devuelve null si salió todo bien, o un mensaje para mostrarle al cliente.
    public String priceStoreOrder(Order order) {
        double subtotal = 0;

        for (OrderItem item : order.getItems()) {
            boolean isCake = Boolean.TRUE.equals(item.getIsCustomCake())
                    || CUSTOM_CAKE.equalsIgnoreCase(item.getProductType());

            if (isCake) {
                // Torta del creador: el precio sale del tamaño elegido
                Optional<CakeSize> size = findCakeSize(item);
                if (size.isEmpty()) {
                    return "El tamaño de una de tus tortas ya no está disponible. Quitala del carrito y armala de nuevo.";
                }
                item.setIsCustomCake(true);
                item.setProductType(CUSTOM_CAKE);
                item.setProduct(null);
                item.setPrice(size.get().getPrice().doubleValue());
            } else {
                // Producto del catálogo: el precio sale de la tabla de productos
                Optional<Product> product = findProduct(item);
                if (product.isEmpty()) {
                    return "\"" + item.getProductType() + "\" ya no está disponible. Quitalo del carrito y volvé a intentar.";
                }
                item.setIsCustomCake(false);
                item.setProduct(product.get());   // además, así el pedido sabe qué receta descontar
                item.setProductType(product.get().getName());
                item.setPrice(product.get().getPrice());
            }
            subtotal += item.getPrice();
        }

        // Cupón: se vuelve a validar acá (pudo vencerse o agotarse mientras el cliente compraba)
        double discount = 0;
        String code = order.getCouponCode();
        order.setCouponCode(null);
        if (code != null && !code.isBlank()) {
            Optional<Coupon> coupon = couponService.findUsable(code);
            if (coupon.isEmpty()) {
                return "El cupón " + code.trim().toUpperCase() + " ya no es válido. Quitalo y volvé a enviar el pedido.";
            }
            Coupon c = coupon.get();
            if (c.getType() == CouponType.DESCUENTO && c.getDiscountPercentage() != null) {
                int percentage = Math.max(0, Math.min(100, c.getDiscountPercentage()));
                discount = subtotal * percentage / 100.0;
            }
            couponService.consume(c);         // se descuenta un uso del cupón
            order.setCouponCode(c.getCode());
        }

        order.setTotalPrice((double) Math.round(subtotal - discount));
        return null;
    }

    // Por id (carritos nuevos) o, si no viene, por el nombre del tamaño que está al principio
    // del detalle: "20 porciones (16x15cm) | Cubierta: …" (carritos guardados antes del cambio)
    private Optional<CakeSize> findCakeSize(OrderItem item) {
        if (item.getCakeSizeId() != null) {
            return cakeSizeRepository.findById(item.getCakeSizeId())
                    .filter(s -> Boolean.TRUE.equals(s.getEnabled()));
        }
        String details = item.getDetails() == null ? "" : item.getDetails();
        String sizeName = details.split("\\|")[0].trim();
        return cakeSizeRepository.findByEnabledTrueOrderByIdAsc().stream()
                .filter(s -> s.getName().equalsIgnoreCase(sizeName))
                .findFirst();
    }

    // Por id o, si no viene, por nombre. Solo productos visibles en la tienda.
    private Optional<Product> findProduct(OrderItem item) {
        if (item.getProduct() != null && item.getProduct().getId() != null) {
            return productRepository.findById(item.getProduct().getId()).filter(Product::isEnabled);
        }
        if (item.getProductType() == null) return Optional.empty();
        return productRepository.findFirstByNameIgnoreCaseAndEnabledTrue(item.getProductType().trim());
    }
}
