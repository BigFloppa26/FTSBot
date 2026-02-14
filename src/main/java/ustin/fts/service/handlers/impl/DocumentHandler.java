package ustin.fts.service.handlers.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ustin.fts.service.UserState;
import ustin.fts.service.UserStateService;
import ustin.fts.service.handlers.CommandHandler;

import java.io.ByteArrayInputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentHandler implements CommandHandler {

    private final UserStateService stateService;

    @Override
    public boolean canHandle(Update update) {
        return update.hasMessage() &&
               update.getMessage().hasDocument();
    }

    @Override
    public void execute(Update update, TelegramClient client) {
        var chatId = update.getMessage().getChatId();
        var document = update.getMessage().getDocument();
        var fileName = document.getFileName();
        var fileExtension = getFileExtension(fileName);

        try {
            // Проверяем, есть ли активное состояние для этого чата
            if (stateService.hasState(chatId)) {
                handleFileWithState(update, client, document, fileExtension);
            } else {
                handleFileWithoutState(update, client, fileName);
            }

        } catch (Exception e) {
            log.error("Error handling document", e);
            sendError(chatId, client, "Ошибка обработки файла");
        }
    }

    private void handleFileWithState(Update update, TelegramClient client,
                                     Document document, String fileExtension) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        UserState state = stateService.getState(chatId);

        // Проверяем, что команда - /fts
        if (!"/fts".equals(state.getCurrentCommand())) {
            handleFileWithoutState(update, client, document.getFileName());
            return;
        }

        // Проверяем расширение файла
        if (!isExpectedFileType(fileExtension, state)) {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ Ожидается файл типа: " + String.join(" или ", state.getExpectedFileTypes()))
                    .build());
            return;
        }

        // Сохраняем файл
        stateService.addReceivedFile(chatId, document.getFileId());

        // Скачиваем и сохраняем файл во временное хранилище
        saveFileLocally(document, client);

        // Получаем обновленное состояние
        state = stateService.getState(chatId);

        // Сообщаем о прогрессе
        int received = state.getReceivedFiles().size();
        int expected = state.getExpectedFiles();

        client.execute(SendMessage.builder()
                .chatId(chatId)
                .text(String.format("✅ Файл %d из %d получен: %s",
                        received, expected, document.getFileName()))
                .build());

        // Если все файлы получены - обрабатываем
        if (stateService.isComplete(chatId)) {
            processAllFiles(update, client, state);
            stateService.removeState(chatId); // очищаем состояние
        }
    }

    private void handleFileWithoutState(Update update, TelegramClient client,
                                        String fileName) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();

        client.execute(SendMessage.builder()
                .chatId(chatId)
                .text("📁 Файл получен: " + fileName +
                      "\nНо я не жду файлы сейчас. Используйте /fts для начала работы.")
                .build());
    }

    private boolean isExpectedFileType(String extension, UserState state) {
        return state.getExpectedFileTypes().stream()
                .anyMatch(type -> type.equalsIgnoreCase(extension));
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    private void saveFileLocally(Document document, TelegramClient client) {
        try {
            GetFile getFile = new GetFile(document.getFileId());
            var file = client.execute(getFile);

            log.info("File saved: {}", document.getFileName());
        } catch (Exception e) {
            log.error("Error saving file", e);
        }
    }

    private void processAllFiles(Update update, TelegramClient client, UserState state) {
        Long chatId = update.getMessage().getChatId();

        try {
            // Отправляем сообщение о начале обработки
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("🔄 Получены все файлы! Начинаю обработку...")
                    .build());

            // Получаем fileId
            List<String> fileIds = state.getReceivedFiles();

            if (fileIds.size() != 2) {
                throw new RuntimeException("Expected 2 files, got " + fileIds.size());
            }

            // Определяем какой файл XLSX, а какой XML
            byte[] xlsxBytes = null;
            byte[] xmlBytes = null;

            // Скачиваем оба файла в байты
            for (int i = 0; i < fileIds.size(); i++) {
                String fileId = fileIds.get(i);
                byte[] fileBytes = downloadFileAsBytes(fileId, client);

                // Получаем оригинальное имя файла из состояния (нужно сохранять)
                // Для простоты определяем по расширению, но лучше сохранять в state
                String fileName = "file_" + i; // нужно сохранять имя файла в state

                if (isXlsxFile(fileBytes)) {
                    xlsxBytes = fileBytes;
                    log.info("XLSX file loaded: {} bytes", xlsxBytes.length);
                } else if (isXmlFile(fileBytes)) {
                    xmlBytes = fileBytes;
                    log.info("XML file loaded: {} bytes", xmlBytes.length);
                }
            }

            if (xlsxBytes == null || xmlBytes == null) {
                throw new RuntimeException("Missing required file types. Need both XLSX and XML files.");
            }

            processXlsxAndXml(xlsxBytes, xmlBytes, chatId, client);

        } catch (Exception e) {
            log.error("Error processing files for chat {}", chatId, e);
            sendError(chatId, client, "Ошибка при обработке файлов: " + e.getMessage());
        }
    }

    private void processXlsxAndXml(byte[] xlsxBytes, byte[] xmlBytes, Long chatId, TelegramClient client) {
        try {
            // Работа с XLSX через Apache POI
            try (var xlsxStream = new ByteArrayInputStream(xlsxBytes);
                 var workbook = new XSSFWorkbook(xlsxStream)) {

                var sheet = workbook.getSheetAt(0);
                log.info("XLSX has {} rows", sheet.getPhysicalNumberOfRows());

                // Читаем данные из Excel
                for (Row row : sheet) {
                    // Ваша логика чтения Excel
                }
            }

            // Работа с XML
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();

            try (var xmlStream = new java.io.ByteArrayInputStream(xmlBytes)) {
                var xmlDoc = builder.parse(xmlStream);

                // Читаем данные из XML
                var nodes = xmlDoc.getElementsByTagName("*");
                log.info("XML has {} nodes", nodes.getLength());

                // Ваша логика обработки XML
            }

            // Здесь ваша бизнес-логика с использованием данных из обоих файлов

            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("✅ Файлы успешно обработаны!")
                    .build());

        } catch (Exception e) {
            log.error("Error processing files", e);
            throw new RuntimeException("Failed to process files", e);
        }
    }

    private boolean isXlsxFile(byte[] bytes) {
        // Проверяем сигнатуру XLSX файла (PK - 50 4B)
        return bytes.length > 4 &&
               bytes[0] == 0x50 && bytes[1] == 0x4B &&
               bytes[2] == 0x03 && bytes[3] == 0x04;
    }

    private boolean isXmlFile(byte[] bytes) {
        // Проверяем начало XML файла (<?xml)
        String start = new String(bytes, 0, Math.min(100, bytes.length), java.nio.charset.StandardCharsets.UTF_8);
        return start.trim().startsWith("<?xml");
    }

    private void sendError(Long chatId, TelegramClient client, String text) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ " + text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send error", e);
        }
    }

    @Override
    public String getCommandName() {
        return "document_handler";
    }

    private byte[] downloadFileAsBytes(String fileId, TelegramClient client) throws Exception {
        GetFile getFile = new GetFile(fileId);
        var file = client.execute(getFile);

        // Получаем URL для скачивания файла
        String fileUrl = "https://api.telegram.org/file/bot" + getToken() + "/" + file.getFilePath();

        // Скачиваем файл как байты
        java.net.URL url = new java.net.URL(fileUrl);
        try (java.io.InputStream is = url.openStream()) {
            return is.readAllBytes();
        }
    }

    private String getToken() {
        return System.getenv("BOT_TOKEN"); // или из @Value
    }
}