package ustin.fts.service;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserState {
    private Long chatId;                    // ID чата
    private String currentCommand;           // Текущая команда (/fts)
    private int expectedFiles;                // Сколько файлов нужно
    private List<String> receivedFiles;       // ID полученных файлов
    private List<String> expectedFileTypes;   // Ожидаемые типы файлов

    public UserState(Long chatId) {
        this.chatId = chatId;
        this.receivedFiles = new ArrayList<>();
        this.expectedFileTypes = new ArrayList<>();
        this.expectedFiles = 0;
        this.currentCommand = null;
    }

    public void addReceivedFile(String fileId) {
        receivedFiles.add(fileId);
    }

    public boolean isComplete() {
        return receivedFiles.size() == expectedFiles;
    }

    public void reset() {
        currentCommand = null;
        expectedFiles = 0;
        receivedFiles.clear();
        expectedFileTypes.clear();
    }
}