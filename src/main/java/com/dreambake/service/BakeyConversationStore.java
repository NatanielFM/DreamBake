package com.dreambake.service;

import com.dreambake.ai.AiMessage;
import com.dreambake.ai.tools.ToolOutcome;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversaciones con Bakey, guardadas EN EL SERVIDOR por usuario (paso 5.5; decisión del autor).
 *
 * Antes vivían en la sesión y se perdían al cerrar sesión. Ahora hay una por usuario: sigue igual al
 * cambiar de pestaña del panel, recargar la página, volver a entrar o abrir el panel en otro dispositivo.
 * Están en memoria (no en la base): si la app se reinicia empiezan de cero. No se usa localStorage,
 * así nada de la conversación queda guardado en la computadora.
 *
 * Cada conversación tiene dos listas:
 *  - forModel: preguntas y respuestas en texto, lo que se le vuelve a mandar a Gemini (últimas 20).
 *  - forDisplay: lo que se dibuja en la ventana del chat (últimos 20 mensajes), con las tarjetas de
 *    acciones y su estado (pendiente, confirmada, cancelada, vencida o falló).
 */
@Component
public class BakeyConversationStore {

    static final int MAX_MESSAGES = 20;

    /** Estado de una tarjeta de acción, tal como la ve la persona. */
    public enum CardState { PENDIENTE, CONFIRMADA, CANCELADA, VENCIDA, FALLO }

    /** Tarjeta de acción para mostrar (datos de ToolOutcome.ActionCard + estado + resultado). */
    public record Card(String id, String title, List<String> lines, String expiresAt, CardState state, String result) {}

    /** Un mensaje de la ventana del chat. role: "user" o "bakey". */
    public record Entry(String role, String text, List<Card> cards) {}

    private static class Conversation {
        final List<AiMessage> forModel = new ArrayList<>();
        final List<Entry> forDisplay = new ArrayList<>();
    }

    private final Map<Long, Conversation> conversations = new ConcurrentHashMap<>();

    private Conversation of(Long userId) {
        return conversations.computeIfAbsent(userId, id -> new Conversation());
    }

    public List<AiMessage> modelHistory(Long userId) {
        Conversation c = of(userId);
        synchronized (c) { return new ArrayList<>(c.forModel); }
    }

    public void saveModelHistory(Long userId, List<AiMessage> history) {
        Conversation c = of(userId);
        synchronized (c) {
            c.forModel.clear();
            c.forModel.addAll(history);
        }
    }

    /** Agrega lo que preguntó la persona y lo que respondió Bakey (con sus tarjetas) a la ventana. */
    public void addExchange(Long userId, String question, String answer, List<ToolOutcome.ActionCard> cards) {
        List<Card> list = cards.stream()
                .map(a -> new Card(a.id(), a.title(), a.lines(), a.expiresAt(), CardState.PENDIENTE, null))
                .toList();
        Conversation c = of(userId);
        synchronized (c) {
            c.forDisplay.add(new Entry("user", question, List.of()));
            c.forDisplay.add(new Entry("bakey", answer, list));
            while (c.forDisplay.size() > MAX_MESSAGES) c.forDisplay.remove(0);
        }
    }

    /** Mensajes para dibujar. Las tarjetas pendientes cuya hora ya pasó se muestran como vencidas. */
    public List<Entry> display(Long userId) {
        Conversation c = of(userId);
        synchronized (c) {
            LocalTime now = LocalTime.now();
            return c.forDisplay.stream().map(e -> new Entry(e.role(), e.text(), e.cards().stream()
                    .map(card -> card.state() == CardState.PENDIENTE && expired(card.expiresAt(), now)
                            ? new Card(card.id(), card.title(), card.lines(), card.expiresAt(), CardState.VENCIDA, null)
                            : card)
                    .toList())).toList();
        }
    }

    /** Después de Confirmar/Cancelar: guarda el nuevo estado y el mensaje del resultado en la tarjeta. */
    public void updateCard(Long userId, String cardId, CardState state, String result) {
        Conversation c = of(userId);
        synchronized (c) {
            for (int i = 0; i < c.forDisplay.size(); i++) {
                Entry e = c.forDisplay.get(i);
                if (e.cards().stream().noneMatch(card -> card.id().equals(cardId))) continue;
                List<Card> updated = e.cards().stream()
                        .map(card -> card.id().equals(cardId)
                                ? new Card(card.id(), card.title(), card.lines(), card.expiresAt(), state, result)
                                : card)
                        .toList();
                c.forDisplay.set(i, new Entry(e.role(), e.text(), updated));
            }
        }
    }

    /** "Nueva conversación": borra todo lo de esa persona. */
    public void reset(Long userId) {
        conversations.remove(userId);
    }

    // La tarjeta guarda la hora de vencimiento como "HH:mm" (vence a los pocos minutos, el mismo día)
    private static boolean expired(String hhmm, LocalTime now) {
        try {
            LocalTime limit = LocalTime.parse(hhmm);
            // Si la hora límite quedó "antes" por pasar la medianoche, más de 12 h de diferencia = no venció
            return now.isAfter(limit) && now.minusHours(12).isBefore(limit);
        } catch (Exception e) {
            return false;
        }
    }
}
