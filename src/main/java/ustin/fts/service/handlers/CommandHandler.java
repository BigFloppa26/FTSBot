package ustin.fts.service.handlers;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public interface CommandHandler {
    boolean canHandle(Update update);
    void execute(Update update, TelegramClient client);
    String getCommandName();
}