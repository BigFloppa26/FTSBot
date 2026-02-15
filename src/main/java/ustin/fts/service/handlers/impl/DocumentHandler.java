package ustin.fts.service.handlers.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ustin.fts.service.UserStateService;
import ustin.fts.service.handlers.CommandHandler;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentHandler implements CommandHandler {

    private final UserStateService stateService;
    @Value("${telegram.bot.token}") private String token;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() && update.getMessage().hasDocument();
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        var chatId = update.getMessage().getChatId();
        var doc = update.getMessage().getDocument();
        var ext = getExt(doc.getFileName());

        try {
            var state = stateService.getState(chatId);
            if (state == null || !"/fts".equals(state.getCurrentCommand())) {
                sendMsg(client, chatId, "📁 Файл получен, но сначала введите /fts");
                return;
            }

            if (!state.getExpectedFileTypes().contains(ext)) {
                sendMsg(client, chatId, "❌ Ожидается: " + String.join(" или ", state.getExpectedFileTypes()));
                return;
            }

            if (state.getReceivedFiles().stream().anyMatch(id -> id.endsWith("." + ext))) {
                sendMsg(client, chatId, "⚠️ Файл ." + ext + " уже загружен");
                return;
            }

            stateService.addReceivedFile(chatId, doc.getFileId() + "." + ext);
            var received = stateService.getState(chatId).getReceivedFiles().size();

            sendMsg(client, chatId, String.format("✅ Файл %d из %d получен", received, state.getExpectedFiles()));

            if (received == state.getExpectedFiles()) {
                processFiles(stateService.getState(chatId).getReceivedFiles(), chatId, client);
                stateService.removeState(chatId);
            }
        } catch (Exception e) {
            log.error("Error", e);
            sendMsg(client, chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void processFiles(List<String> fileIds, Long chatId, TelegramClient client) throws Exception {
        sendMsg(client, chatId, "🔄 Обработка...");

        byte[] xlsx = null, xml = null;
        for (String id : fileIds) {
            var parts = id.split("\\.(?=[^\\.]+$)");
            var bytes = download(parts[0], client);

            if ("xlsx".equals(parts[1]) || (bytes[0] == 0x50 && bytes[1] == 0x4B)) xlsx = bytes;
            else if ("xml".equals(parts[1]) || new String(bytes, 0, 100, StandardCharsets.UTF_8).trim().startsWith("<?xml")) xml = bytes;
        }

        if (xlsx == null || xml == null) throw new RuntimeException("Нужны XLSX и XML");

        // ============ ВАША ЛОГИКА ЗДЕСЬ ============

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            log.info("XLSX строк: {}", wb.getSheetAt(0).getPhysicalNumberOfRows());
        }

        var xmlDoc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        log.info("XML узлов: {}", xmlDoc.getElementsByTagName("*").getLength());

        // TODO: Добавьте свою логику обработки данных
        // Доступны: xlsx (byte[]), xml (byte[]), chatId, client

        // ===========================================

        sendMsg(client, chatId, "✅ Готово!");
    }

    private byte[] download(String fileId, TelegramClient client) throws Exception {
        var file = client.execute(new GetFile(fileId));
        try (var is = new URL("https://api.telegram.org/file/bot" + token + "/" + file.getFilePath()).openStream()) {
            return is.readAllBytes();
        }
    }

    private void sendMsg(TelegramClient client, Long chatId, String text) {
        try { client.execute(SendMessage.builder().chatId(chatId).text(text).build()); }
        catch (Exception e) { log.error("Send error", e); }
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    @Override public String getCommandName() { return "document_handler"; }
}