package com.dreambake.controller;

import com.dreambake.audit.Auditable;
import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditModule;
import com.dreambake.service.AuditService;
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

    // Registro de auditoría (paso 3.3): se usa directo donde hace falta el antes y el después
    @Autowired
    private AuditService audit;

    @GetMapping
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @PostMapping
    @Auditable(action = AuditAction.CREATE, module = AuditModule.CUPONES, entityType = "Cupón", entityId = "#result.code",
            description = "Creó el cupón #{#result.code}#{#result.discountPercentage != null ? ' (' + #result.discountPercentage + '% de descuento)' : ''}")
    public Coupon createCoupon(@RequestBody Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id, @RequestBody Coupon couponDetails) {
        Optional<Coupon> optionalCoupon = couponRepository.findById(id);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            Map<String, Object> before = couponSnapshot(coupon); // antes (por ejemplo, otro % de descuento)
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
            Coupon saved = couponRepository.save(coupon);
            Map<String, Object> diff = AuditText.diff(before, couponSnapshot(saved));
            audit.log(AuditAction.UPDATE, AuditModule.CUPONES, "Cupón", saved.getCode(),
                    "Editó el cupón " + saved.getCode() + AuditText.changedFields(diff), diff);
            return ResponseEntity.ok(saved);
        }
        return ResponseEntity.notFound().build();
    }

    // Los datos de un cupón que se comparan en la auditoría
    private Map<String, Object> couponSnapshot(Coupon c) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("codigo", c.getCode());
        m.put("tipo", c.getType() == null ? null : c.getType().name());
        m.put("activo", c.isActive());
        m.put("descuento", c.getDiscountPercentage());
        m.put("limite", c.isUnlimited() ? "sin límite" : c.getUsageLimit());
        m.put("texto", c.getCustomText());
        return m;
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
        audit.log(AuditAction.DELETE, AuditModule.CUPONES, "Cupón", coupon.getCode(),
                "Eliminó el cupón " + coupon.getCode(), AuditText.change(couponSnapshot(coupon), null));
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
