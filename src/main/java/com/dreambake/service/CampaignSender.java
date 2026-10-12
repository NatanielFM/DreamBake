package com.dreambake.service;

import com.dreambake.entity.*;
import com.dreambake.repository.CampaignRepository;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.EmailLogRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Envía las campañas en segundo plano, de a un email por vez.
 *
 *  - Tiene su propio "trabajador" (un solo hilo): las campañas salen de a una y no ocupan
 *    el hilo de las pantallas. El administrador puede seguir usando el panel mientras tanto.
 *  - Entre email y email espera 2 segundos, para no superar los límites de Gmail.
 *  - Tope diario (Ajustes de Fidelización, por defecto 400 emails, contando TODOS los emails del día):
 *    al llegar, la campaña se pausa y sigue al día siguiente (la retoma CampaignScheduler).
 *  - Si fallan 3 emails seguidos (por ejemplo, Gmail rechaza la contraseña), se pausa una hora
 *    para no gastar la lista entera en errores.
 *  - Antes de cada email mira si la cancelaron, y saltea a quien ya tiene un intento de esta
 *    campaña en el historial: así, si la PC se apagó a la mitad, se retoma sin repetir a nadie.
 */
@Service
public class CampaignSender {

    private static final Logger log = LoggerFactory.getLogger(CampaignSender.class);

    static final long PAUSE_BETWEEN_EMAILS_MS = 2000;
    static final int MAX_CONSECUTIVE_FAILURES = 3;

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private EmailService emailService;
    @Autowired private BusinessSettingService settingService;
    // @Lazy: CampaignService también usa CampaignSender (se necesitan mutuamente)
    @Autowired @Lazy private CampaignService campaignService;

    // El trabajador: un solo hilo, que se apaga junto con la app
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "envio-campanas");
        t.setDaemon(true);
        return t;
    });

    // Campañas en la fila o enviándose (para no lanzar dos veces la misma)
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public boolean isRunning(Long campaignId) {
        return running.contains(campaignId);
    }

    // Pone la campaña en la fila del trabajador (si ya estaba, no hace nada)
    public void launch(Long campaignId) {
        if (!running.add(campaignId)) return;
        worker.submit(() -> {
            try {
                run(campaignId);
            } catch (Exception e) {
                log.error("Campaña #{}: se interrumpió el envío: {}", campaignId, e.getMessage());
            } finally {
                running.remove(campaignId);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ¿Cuántos emails (de cualquier tipo) salieron hoy?
    private long sentToday() {
        LocalDate today = LocalDate.now(BirthdayService.ZONE);
        return emailLogRepository.countByStatusAndSentAtBetween(EmailStatus.SENT,
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    private void run(Long id) throws InterruptedException {
        Campaign campaign = campaignRepository.findById(id).orElse(null);
        if (campaign == null || campaign.getStatus() != CampaignStatus.SENDING) return;
        // Sin correo configurado no se intenta nada: se pausa una hora en vez de marcar
        // a todos los destinatarios como fallidos (se retoma cuando se cargue el correo)
        if (!emailService.estaConfigurado()) {
            campaignRepository.pause(id, "En pausa: el correo no está configurado (Ajustes → Correo de envío).",
                    LocalDateTime.now().plusHours(1));
            log.warn("Campaña #{}: en pausa porque el correo no está configurado.", id);
            return;
        }
        campaignRepository.clearPause(id);

        List<Customer> recipients = campaignService.audience(campaign.getAudience(), campaign.getTag());
        Map<String, Object> vars = campaignService.variables(campaign, false);
        Resource image = campaignService.imageResource(campaign);
        if (campaign.getImageUrl() != null && image == null) {
            log.warn("Campaña #{}: la foto {} ya no existe; se manda sin foto.", id, campaign.getImageUrl());
            vars.remove("imagenSrc");
        }
        log.info("Campaña #{} '{}': empieza/retoma el envío a {} destinatarios.", id, campaign.getName(), recipients.size());

        int consecutiveFailures = 0;
        for (Customer target : recipients) {
            // ¿La cancelaron mientras tanto?
            CampaignStatus status = campaignRepository.findById(id).map(Campaign::getStatus).orElse(null);
            if (status != CampaignStatus.SENDING) {
                log.info("Campaña #{}: detenida ({}).", id, status);
                return;
            }
            // Ya tiene un intento de esta campaña: no se repite
            if (emailLogRepository.existsByCampaignIdAndCustomer(id, target)) continue;

            // Se recarga el cliente: si se dio de baja durante el envío, no se le manda
            Customer customer = customerRepository.findById(target.getId()).orElse(null);
            if (customer == null || !Boolean.TRUE.equals(customer.getMarketingConsent())
                    || customer.getEmail() == null || customer.getEmail().isBlank()) continue;

            // Tope diario: se pausa hasta mañana
            int limit = settingService.getCampaignDailyLimit();
            if (sentToday() >= limit) {
                LocalDateTime tomorrow = LocalDate.now(BirthdayService.ZONE).plusDays(1).atStartOfDay();
                campaignRepository.pause(id, "Llegó al tope diario de " + limit + " emails: sigue mañana.", tomorrow);
                log.info("Campaña #{}: tope diario de {} emails alcanzado; sigue mañana.", id, limit);
                return;
            }

            EmailService.Resultado r;
            try {
                r = emailService.enviar(customer.getEmail(), campaign.getSubject(), "email/campana", vars,
                        EmailType.PROMOTION, customer, id, image).get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                r = new EmailService.Resultado(false, "No se pudo confirmar el envío.");
            }
            updateCounts(id);

            if (r.ok()) {
                consecutiveFailures = 0;
            } else if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                campaignRepository.pause(id, "Se pausó una hora: fallaron " + MAX_CONSECUTIVE_FAILURES
                        + " emails seguidos (" + r.mensaje() + ")", LocalDateTime.now().plusHours(1));
                log.warn("Campaña #{}: {} fallos seguidos, se pausa una hora. Último error: {}", id,
                        MAX_CONSECUTIVE_FAILURES, r.mensaje());
                return;
            }
            Thread.sleep(PAUSE_BETWEEN_EMAILS_MS);
        }

        // No queda nadie sin intentar: terminada
        int sent = (int) emailLogRepository.countByCampaignIdAndStatus(id, EmailStatus.SENT);
        int failed = (int) emailLogRepository.countByCampaignIdAndStatus(id, EmailStatus.FAILED);
        campaignRepository.updateCounts(id, sent, failed);
        campaignRepository.finish(id, sent + failed);
        log.info("Campaña #{}: terminada. {} enviados, {} fallidos.", id, sent, failed);
    }

    private void updateCounts(Long id) {
        campaignRepository.updateCounts(id,
                (int) emailLogRepository.countByCampaignIdAndStatus(id, EmailStatus.SENT),
                (int) emailLogRepository.countByCampaignIdAndStatus(id, EmailStatus.FAILED));
    }
}
