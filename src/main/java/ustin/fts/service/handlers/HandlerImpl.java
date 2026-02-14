package ustin.fts.service.handlers;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class HandlerImpl implements Handler {

    private final List<CommandHandler> commandHandlers;
    private TelegramClient client;
    private final Map<String, CommandHandler> textCommandMap = new ConcurrentHashMap<>();
    private final List<CommandHandler> allHandlers;

    public HandlerImpl(List<CommandHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
        this.allHandlers = commandHandlers;
    }

    @PostConstruct
    public void init() {
        for (CommandHandler handler : commandHandlers) {
            var commandName = handler.getCommandName();
            if (commandName != null && !commandName.isEmpty()) {
                textCommandMap.put(commandName, handler);
                log.info("Registered text command: '{}' -> {}", commandName, handler.getClass().getSimpleName());
            } else {
                log.info("Registered non-text handler: {}", handler.getClass().getSimpleName());
            }
        }
        log.info("Total handlers loaded: {}", commandHandlers.size());
    }

    @Autowired
    public void setClient(TelegramClient client) {
        this.client = client;
    }

    @Override
    public void handle(Update update) {
        try {
            if (!update.hasMessage()) {return;}

            var handler = findHandler(update);

            if (handler != null) {
                log.info("Found handler: {}", handler.getClass().getSimpleName());
                handler.execute(update, client);
            } else {
                log.warn("No handler found for update");
                handleNoHandler(update);
            }

        } catch (Exception e) {
            log.error("Error handling update", e);
            sendErrorMessage(update);
        }
    }

    private CommandHandler findHandler(Update update) {
        var message = update.getMessage();

        for (CommandHandler handler : allHandlers) {
            try {
                if (handler.canHandle(update)) {
                    log.debug("Handler {} can handle this update", handler.getClass().getSimpleName());
                    return handler;
                }
            } catch (Exception e) {
                log.error("Error checking canHandle for {}", handler.getClass().getSimpleName(), e);
            }
        }

        if (message.hasText()) {
            var text = message.getText();
            var handler = textCommandMap.get(text);
            if (handler != null && handler.canHandle(update)) {
                return handler;
            }
        }

        return null;
    }

    private void handleNoHandler(Update update) {
        var chatId = update.getMessage().getChatId();

        if (update.getMessage().hasDocument()) {
            sendText(chatId, "📁 Файл получен, но я не жду файлы сейчас.");
        } else if (update.getMessage().hasText()) {
            sendText(chatId, "Неизвестная команда. Используйте /start");
        }
    }

    private void sendErrorMessage(Update update) {
        if (update.hasMessage()) {
            sendText(update.getMessage().getChatId(), "❌ " + "Произошла внутренняя ошибка");
        }
    }

    private void sendText(Long chatId, String text) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat: {}", chatId, e);
        }
    }
}