package ustin.fts.service.handlers.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ustin.fts.service.UserState;
import ustin.fts.service.UserStateService;
import ustin.fts.service.handlers.CommandHandler;
import ustin.fts.xml.model.DTData;
import ustin.fts.xml.service.XmlService;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentHandler implements CommandHandler {

    private final UserStateService stateService;
    private final XmlService xmlService;

    @Value("${telegram.bot.token}")
    private String token;

    private static final String XLSX_EXT = "xlsx";
    private static final String XML_EXT = "xml";
    private static final String PROCESS = "/process";
    private static final String CANCEL = "/cancel";

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage()) return false;
        var msg = update.getMessage();
        return msg.hasDocument() ||
               (msg.hasText() && (msg.getText().equals(PROCESS) || msg.getText().equals(CANCEL)));
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        var chatId = update.getMessage().getChatId();
        try {
            var state = stateService.getState(chatId);
            if (state == null || !"/fts".equals(state.getCurrentCommand())) {
                if (update.getMessage().hasDocument())
                    sendMsg(client, chatId, "📁 Сначала введите /fts");
                return;
            }

            if (update.getMessage().hasText()) {
                handleText(update, client, chatId, state);
            } else {
                handleDoc(update, client, chatId, state);
            }
        } catch (Exception e) {
            log.error("Error", e);
            sendMsg(client, chatId, "❌ " + e.getMessage());
        }
    }

    private void handleText(Update update, TelegramClient client, Long chatId, UserState state) {
        var text = update.getMessage().getText();
        if (text.equals(PROCESS)) {
            processAll(client, chatId, state);
        } else if (text.equals(CANCEL)) {
            stateService.removeState(chatId);
            sendMsg(client, chatId, "❌ Отменено");
        }
    }

    private void processAll(TelegramClient client, Long chatId, UserState state) {
        var files = state.getReceivedFiles();
        var hasXlsx = files.stream().anyMatch(f -> f.endsWith("." + XLSX_EXT));
        var xmlCount = files.stream().filter(f -> f.endsWith("." + XML_EXT)).count();

        if (!hasXlsx || xmlCount == 0) {
            sendMsg(client, chatId, "❌ Нужен 1 XLSX и минимум 1 XML");
            return;
        }

        sendMsg(client, chatId, "🔄 Обработка...");
        try {
            processFiles(files, chatId, client);
            stateService.removeState(chatId);
        } catch (Exception e) {
            log.error("Error", e);
            sendMsg(client, chatId, "❌ " + e.getMessage());
        }
    }

    private void handleDoc(Update update, TelegramClient client, Long chatId, UserState state) {
        var doc = update.getMessage().getDocument();
        var ext = getExt(doc.getFileName());
        var files = state.getReceivedFiles();

        if (!ext.equals(XLSX_EXT) && !ext.equals(XML_EXT)) {
            sendMsg(client, chatId, "❌ Только .xlsx и .xml");
            return;
        }

        if (ext.equals(XLSX_EXT) && files.stream().anyMatch(f -> f.endsWith("." + XLSX_EXT))) {
            sendMsg(client, chatId, "❌ Только один XLSX");
            return;
        }

        stateService.addReceivedFile(chatId, doc.getFileId() + "." + ext);

        var xmlCount = state.getReceivedFiles().stream().filter(f -> f.endsWith("." + XML_EXT)).count();
        var hasXlsx = state.getReceivedFiles().stream().anyMatch(f -> f.endsWith("." + XLSX_EXT));

        sendMsg(client, chatId, String.format(
                "✅ Загружено\n📊 XLSX: %s\n📄 XML: %d\n\n%s - старт\n%s - отмена",
                hasXlsx ? "1/1" : "0/1", xmlCount, PROCESS, CANCEL));
    }

    private void processFiles(List<String> fileIds, Long chatId, TelegramClient client) throws Exception {
        byte[] xlsxData = null;
        List<byte[]> xmlList = new ArrayList<>();

        for (String id : fileIds) {
            var parts = id.split("\\.(?=[^.]+$)");
            var bytes = download(parts[0], client);
            if (XLSX_EXT.equals(parts[1])) {
                xlsxData = bytes;
            } else {
                xmlList.add(bytes);
            }
        }

        if (xlsxData == null || xmlList.isEmpty())
            throw new RuntimeException("Нет файлов");

        var dtList = xmlList.stream()
                .map(xmlService::parseXml)
                .peek(dt -> log.info("ДТ: {}", dt))
                .toList();

        // ========== РАБОТА С XLSX ==========
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxData))) {
            var sheet = wb.getSheetAt(0);
            // TODO: Ваша логика с dtList и sheet
            log.info("XLSX: {} строк, XML: {}", sheet.getPhysicalNumberOfRows(), dtList.size());
        }
        // ===================================

        sendMsg(client, chatId, String.format("✅ Готово: %d XML", dtList.size()));
    }

    private byte[] download(String fileId, TelegramClient client) throws Exception {
        var file = client.execute(new GetFile(fileId));
        try (var is = new URL("https://api.telegram.org/file/bot" + token + "/" + file.getFilePath()).openStream()) {
            return is.readAllBytes();
        }
    }

    private void sendMsg(TelegramClient client, Long chatId, String text) {
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (Exception e) {
            log.error("Send error", e);
        }
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    @Override
    public String getCommandName() { return "document_handler"; }
}