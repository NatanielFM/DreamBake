package com.dreambake.controller;

import com.dreambake.entity.Campaign;
import com.dreambake.entity.Coupon;
import com.dreambake.entity.CouponType;
import com.dreambake.repository.CampaignRepository;
import com.dreambake.repository.CouponRepository;
import com.dreambake.service.CampaignService;
import com.dreambake.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/coupons")
public class CouponRestController {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CampaignRepository campaignRepository;

    @GetMapping
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @PostMapping
    public Coupon createCoupon(@RequestBody Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id, @RequestBody Coupon couponDetails) {
        Optional<Coupon> optionalCoupon = couponRepository.findById(id);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            coupon.setCode(couponDetails.getCode());
            coupon.setType(couponDetails.getType());
            coupon.setActive(couponDetails.isActive());
            coupon.setDescription(couponDetails.getDescription());
            coupon.setDiscountPercentage(couponDetails.getDiscountPercentage());
            coupon.setCustomText(couponDetails.getCustomText());
            coupon.setUsageLimit(couponDetails.getUsageLimit());
            coupon.setUnlimited(couponDetails.isUnlimited());
            // OJO: expirationDate no se toca acá. El formulario de cupones del panel no la maneja
            // (la usan los cupones de cumpleaños) y, si se copiara, editar un cupón la borraría.
            return ResponseEntity.ok(couponRepository.save(coupon));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCoupon(@PathVariable Long id) {
        Optional<Coupon> found = couponRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        Coupon coupon = found.get();
        // Si lo usa una campaña que todavía no terminó, no se deja borrar (el email lo mostraría)
        if (campaignRepository.existsByCouponAndStatusIn(coupon, CampaignService.UNFINISHED)) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Este cupón lo usa una campaña que todavía no se terminó de enviar. Quitalo de la campaña antes de borrarlo."));
        }
        // En las campañas ya terminadas solo se le quita la referencia (el historial queda)
        for (Campaign c : campaignRepository.findByCoupon(coupon)) {
            c.setCoupon(null);
            campaignRepository.save(c);
        }
        couponRepository.delete(coupon);
        return ResponseEntity.ok().build();
    }

    // Lo que ve la tienda de un cupón: solo lo necesario para mostrar el beneficio.
    // No incluye el id, los usos que le quedan ni si está activo (datos internos del negocio).
    public record PublicCoupon(String code, CouponType type, Integer discountPercentage, String customText) {}

    // Lo usa el carrito de la tienda (público) para mostrar el descuento antes de enviar el pedido.
    // Solo consulta: el uso del cupón se descuenta en el servidor al guardar el pedido
    // (OrderRestController), así nadie puede gastar los usos de un código desde afuera.
    // Si el cupón no existe, está inactivo o se agotó, la respuesta es la misma (404 vacío):
    // así nadie puede averiguar qué códigos existen probando.
    @GetMapping("/validate/{code}")
    public ResponseEntity<PublicCoupon> validateCoupon(@PathVariable String code) {
        return couponService.findUsable(code)
                .map(c -> ResponseEntity.ok(new PublicCoupon(c.getCode(), c.getType(),
                        c.getDiscountPercentage(), c.getCustomText())))
                .orElse(ResponseEntity.notFound().build());
    }
}
