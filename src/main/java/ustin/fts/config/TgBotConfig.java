package ustin.fts.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ustin.fts.service.FTSBot;

@Configuration
@RequiredArgsConstructor
public class TgBotConfig {

    @Value("${telegram.bot.token}")
    private String token;

    private final FTSBot ftsBot;

    @PostConstruct
    public void registerBot() {
        try {
            var botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(token, ftsBot);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to register bot", e);
        }
    }
}
