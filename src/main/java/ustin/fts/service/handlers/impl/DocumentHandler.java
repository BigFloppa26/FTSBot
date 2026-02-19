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
import org.w3c.dom.*;
import ustin.fts.service.UserState;
import ustin.fts.service.UserStateService;
import ustin.fts.service.handlers.CommandHandler;
import ustin.fts.xml.model.DTData;
import ustin.fts.xml.service.XmlService;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
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
    private static final String PROCESS_COMMAND = "/process";
    private static final String CANCEL_COMMAND = "/cancel";

    @Override
    public boolean canHandle(Update update) {
        if (update.hasMessage()) {
            var message = update.getMessage();
            if (message.hasDocument()) return true;
            if (message.hasText()) {
                var text = message.getText();
                return text.equals(PROCESS_COMMAND) || text.equals(CANCEL_COMMAND);
            }
        }
        return false;
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        var chatId = update.getMessage().getChatId();

        try {
            var state = stateService.getState(chatId);
            if (state == null || !"/fts".equals(state.getCurrentCommand())) {
                if (update.getMessage().hasDocument()) {
                    sendMsg(client, chatId, "📁 Файл получен, но сначала введите /fts");
                }
                return;
            }

            if (update.getMessage().hasText()) {
                handleTextCommand(update, client, chatId, state);
            } else if (update.getMessage().hasDocument()) {
                handleDocument(update, client, chatId, state);
            }
        } catch (Exception e) {
            log.error("Error", e);
            sendMsg(client, chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleTextCommand(Update update, TelegramClient client, Long chatId, UserState state) {
        var text = update.getMessage().getText();
        if (text.equals(PROCESS_COMMAND)) {
            handleProcessCommand(client, chatId, state);
        } else if (text.equals(CANCEL_COMMAND)) {
            stateService.removeState(chatId);
            sendMsg(client, chatId, "❌ Загрузка отменена");
        }
    }

    private void handleProcessCommand(TelegramClient client, Long chatId, UserState state) {
        List<String> receivedFiles = state.getReceivedFiles();

        boolean hasXlsx = receivedFiles.stream().anyMatch(id -> id.endsWith("." + XLSX_EXT));
        long xmlCount = receivedFiles.stream().filter(id -> id.endsWith("." + XML_EXT)).count();

        if (!hasXlsx || xmlCount == 0) {
            sendMsg(client, chatId, "❌ Нужен 1 XLSX и минимум 1 XML");
            return;
        }

        sendMsg(client, chatId, "🔄 Начинаем обработку...");

        try {
            processFiles(receivedFiles, chatId, client);
            stateService.removeState(chatId);
        } catch (Exception e) {
            log.error("Processing error", e);
            sendMsg(client, chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleDocument(Update update, TelegramClient client, Long chatId, UserState state) {
        var doc = update.getMessage().getDocument();
        var ext = getExt(doc.getFileName());
        List<String> receivedFiles = state.getReceivedFiles();

        if (!ext.equals(XLSX_EXT) && !ext.equals(XML_EXT)) {
            sendMsg(client, chatId, "❌ Только .xlsx и .xml");
            return;
        }

        if (ext.equals(XLSX_EXT) && receivedFiles.stream().anyMatch(id -> id.endsWith("." + XLSX_EXT))) {
            sendMsg(client, chatId, "❌ Можно только один XLSX");
            return;
        }

        stateService.addReceivedFile(chatId, doc.getFileId() + "." + ext);

        long xmlCount = state.getReceivedFiles().stream().filter(id -> id.endsWith("." + XML_EXT)).count();
        boolean hasXlsx = state.getReceivedFiles().stream().anyMatch(id -> id.endsWith("." + XLSX_EXT));

        sendMsg(client, chatId, String.format(
                "✅ Загружено\n📊 XLSX: %s\n📄 XML: %d\n\n%s - старт\n%s - отмена",
                hasXlsx ? "1/1" : "0/1", xmlCount, PROCESS_COMMAND, CANCEL_COMMAND));
    }

    private void processFiles(List<String> fileIds, Long chatId, TelegramClient client) throws Exception {
        byte[] xlsxData = null;
        List<byte[]> xmlFilesList = new ArrayList<>();

        for (String fileId : fileIds) {
            String[] parts = fileId.split("\\.(?=[^.]+$)");
            byte[] bytes = download(parts[0], client);
            String ext = parts[1];

            if (XLSX_EXT.equals(ext)) {
                xlsxData = bytes;
            } else if (XML_EXT.equals(ext)) {
                xmlFilesList.add(bytes);  // Добавляем байты XML в список
            }
        }

        if (xlsxData == null || xmlFilesList.isEmpty()) {
            throw new RuntimeException("Нет XLSX или XML файлов");
        }

        // Парсим все XML файлы
        List<DTData> allDtData = new ArrayList<>();
        for (byte[] xmlBytes : xmlFilesList) {
            var dtData = xmlService.parseXml(xmlBytes);
            allDtData.add(dtData);
            log.info("ДТ: {}", dtData);
        }

        // ==================== РАБОТА С XLSX ФАЙЛОМ ====================
        // xlsxData - байты XLSX файла
        // allDtData - список объектов DTData с данными из всех XML
        // xmlFilesList - список байтов XML файлов (если нужен доступ к исходным данным)

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxData))) {
            var sheet = workbook.getSheetAt(0);

            // TODO: Здесь ваша логика работы с Excel


            log.info("XLSX обработан, строк: {}, XML файлов: {}",
                    sheet.getPhysicalNumberOfRows(), allDtData.size());
        }
        // =============================================================

        sendMsg(client, chatId, "✅ Обработано файлов:\n" + "📊 XLSX: 1 файл\n" +
                                String.format("📄 XML: %d файлов\n\n", allDtData.size()));
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
    public String getCommandName() {
        return "document_handler";
    }
}