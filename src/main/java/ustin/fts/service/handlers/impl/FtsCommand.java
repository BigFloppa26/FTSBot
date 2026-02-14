package ustin.fts.service.handlers.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ustin.fts.service.UserStateService;
import ustin.fts.service.handlers.CommandHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class FtsCommand implements CommandHandler {

    private final UserStateService stateService;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() &&
               update.getMessage().hasText() &&
               "/fts".equals(update.getMessage().getText());
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        Long chatId = update.getMessage().getChatId();

        try {
            // Устанавливаем ожидание 2 файлов (xlsx и xml)
            stateService.setWaitingForFiles(chatId, "/fts", 2, "xlsx", "xml");

            String message = """
                    📦 Режим FTS активирован!
                    
                    Ожидаю загрузку двух файлов:
                    1️⃣ Файл Excel (.xlsx)
                    2️⃣ Файл XML (.xml)
                    
                    Пожалуйста, отправьте файлы по очереди.
                    """;

            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(message)
                    .build());

            log.info("FTS command started for chat: {}", chatId);

        } catch (TelegramApiException e) {
            log.error("Failed to execute FTS command", e);
        }
    }

    @Override
    public String getCommandName() {
        return "/fts";
    }
}