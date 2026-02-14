package ustin.fts.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ustin.fts.service.FTSBot;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotRegistrar {

    @Value("${telegram.bot.token}")
    private String token;

    private final FTSBot ftsBot;
    private TelegramBotsLongPollingApplication botsApplication;

    @PostConstruct
    public void register() {
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(token, ftsBot);
            log.info("Bot registered successfully");
        } catch (TelegramApiException e) {
            log.error("Failed to register bot", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (botsApplication != null) {
            try {
                botsApplication.close();
                log.info("Bot application closed");
            } catch (Exception e) {
                log.error("Error closing bot", e);
            }
        }
    }
}