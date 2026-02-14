package ustin.fts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import ustin.fts.service.handlers.Handler;

@Slf4j
@Service
@RequiredArgsConstructor
public class FTSBot implements LongPollingSingleThreadUpdateConsumer {

    private final Handler handler;

    @Override
    public void consume(Update update) {
        handler.handle(update);
    }
}
