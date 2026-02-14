package ustin.fts.service.handlers;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface Handler {

    void handle(Update update);
}
