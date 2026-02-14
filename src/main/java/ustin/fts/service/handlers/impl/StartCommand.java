package ustin.fts.service.handlers.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ustin.fts.service.handlers.CommandHandler;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommand implements CommandHandler {

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() &&
               update.getMessage().hasText() &&
               "/start".equals(update.getMessage().getText());
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        var chatId = update.getMessage().getChatId();
        var userName = update.getMessage().getFrom().getUserName();

        try {
            var keyboard = createKeyboard();
            var welcomeText = String.format(
                    """
                    Привет, %s! 👋
                    
                    Нажмите кнопку /fts чтобы начать загрузку файлов:
                    • 1 файл Excel (.xlsx)
                    • 1 файл XML (.xml)
                    """,
                    userName != null ? "@" + userName : "пользователь"
            );

            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(welcomeText)
                    .replyMarkup(keyboard)
                    .build());

            log.info("Start command executed for chat: {}", chatId);

        } catch (TelegramApiException e) {
            log.error("Failed to execute start command for chat: {}", chatId, e);
            sendErrorMessage(chatId, client);
        }
    }

    /**
     * Создает клавиатуру с одной кнопкой /fts
     */
    private ReplyKeyboardMarkup createKeyboard() {
        var row = new KeyboardRow();
        row.add(KeyboardButton.builder()
                .text("/fts")
                .build());

        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row))  // добавляем ряд в клавиатуру
                .resizeKeyboard(true)     // автоматически подгонять размер кнопок
                .oneTimeKeyboard(false)    // не скрывать после нажатия
                .selective(false)          // показывать всем
                .build();
    }

    /**
     * Отправляет сообщение об ошибке
     */
    private void sendErrorMessage(Long chatId, TelegramClient client) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ Произошла ошибка. Попробуйте позже.")
                    .build());
        } catch (TelegramApiException ex) {
            log.error("Failed to send error message to chat: {}", chatId, ex);
        }
    }

    @Override
    public String getCommandName() {
        return "/start";
    }
}