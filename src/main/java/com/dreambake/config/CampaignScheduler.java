package com.dreambake.config;

import com.dreambake.entity.Campaign;
import com.dreambake.entity.CampaignStatus;
import com.dreambake.repository.CampaignRepository;
import com.dreambake.service.CampaignSender;
import com.dreambake.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Cada 5 minutos revisa las campañas:
 *  - Las programadas cuya hora ya llegó: si se pueden mandar, empiezan; si no (por ejemplo,
 *    el cupón venció), vuelven a borrador con un aviso.
 *  - Las que estaban enviándose y quedaron en pausa (tope diario, errores seguidos o la PC
 *    se apagó a la mitad): si ya es hora, se retoman donde quedaron.
 */
@Component
public class CampaignScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampaignScheduler.class);

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignService campaignService;
    @Autowired private CampaignSender sender;

    // Cron: "en el segundo 0 de cada minuto múltiplo de 5" → 9:00, 9:05, 9:10…
    @Scheduled(cron = "0 */5 * * * *", zone = "America/Argentina/Buenos_Aires")
    public void checkCampaigns() {
        LocalDateTime now = LocalDateTime.now();

        for (Campaign c : campaignRepository.findByStatusAndScheduledAtLessThanEqual(CampaignStatus.SCHEDULED, now)) {
            String problem = campaignService.validateForSend(c, true);
            if (problem == null) {
                log.info("Campaña #{} '{}': llegó su hora programada, empieza el envío.", c.getId(), c.getName());
                campaignService.start(c);
            } else {
                c.setStatus(CampaignStatus.DRAFT);
                c.setStatusNote("No se envió a la hora programada: " + problem);
                campaignRepository.save(c);
                log.warn("Campaña #{} '{}': no se pudo enviar a la hora programada: {}", c.getId(), c.getName(), problem);
            }
        }

        for (Campaign c : campaignRepository.findByStatus(CampaignStatus.SENDING)) {
            boolean due = c.getNextAttemptAt() == null || !c.getNextAttemptAt().isAfter(now);
            if (due && !sender.isRunning(c.getId())) {
                sender.launch(c.getId());
            }
        }
    }
}
