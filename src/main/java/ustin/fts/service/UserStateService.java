package ustin.fts.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserStateService {

    // Хранилище состояний для каждого чата (потокобезопасное)
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();

    /**
     * Получить состояние пользователя или создать новое
     */
    public UserState getOrCreateState(Long chatId) {
        return userStates.computeIfAbsent(chatId, id -> {
            log.debug("Creating new state for chat: {}", id);
            return new UserState(id);
        });
    }

    /**
     * Получить состояние пользователя
     */
    public UserState getState(Long chatId) {
        return userStates.get(chatId);
    }

    /**
     * Проверить, есть ли состояние у пользователя
     */
    public boolean hasState(Long chatId) {
        return userStates.containsKey(chatId);
    }

    /**
     * Удалить состояние пользователя
     */
    public void removeState(Long chatId) {
        UserState removed = userStates.remove(chatId);
        if (removed != null) {
            log.debug("Removed state for chat: {}", chatId);
        }
    }

    /**
     * Установить ожидание файлов для пользователя
     */
    public void setWaitingForFiles(Long chatId, String command, int expectedCount, String... fileTypes) {
        UserState state = getOrCreateState(chatId);
        state.setCurrentCommand(command);
        state.setExpectedFiles(expectedCount);
        state.getReceivedFiles().clear();
        state.getExpectedFileTypes().clear();

        for (String type : fileTypes) {
            state.getExpectedFileTypes().add(type.toLowerCase());
        }

        log.info("Set waiting for files for chat {}: command={}, expected={}, types={}",
                chatId, command, expectedCount, String.join(", ", fileTypes));
    }

    /**
     * Добавить полученный файл
     */
    public void addReceivedFile(Long chatId, String fileId) {
        UserState state = getState(chatId);
        if (state != null) {
            state.addReceivedFile(fileId);
            log.debug("Added file for chat {}. Progress: {}/{}",
                    chatId, state.getReceivedFiles().size(), state.getExpectedFiles());
        }
    }

    /**
     * Проверить, все ли файлы получены
     */
    public boolean isComplete(Long chatId) {
        UserState state = getState(chatId);
        return state != null && state.isComplete();
    }

    /**
     * Получить текущую команду пользователя
     */
    public String getCurrentCommand(Long chatId) {
        UserState state = getState(chatId);
        return state != null ? state.getCurrentCommand() : null;
    }

    /**
     * Сбросить состояние пользователя
     */
    public void resetState(Long chatId) {
        UserState state = getState(chatId);
        if (state != null) {
            state.reset();
            log.debug("Reset state for chat: {}", chatId);
        }
    }

    /**
     * Получить ожидаемые типы файлов
     */
    public java.util.List<String> getExpectedFileTypes(Long chatId) {
        UserState state = getState(chatId);
        return state != null ? state.getExpectedFileTypes() : java.util.Collections.emptyList();
    }

    /**
     * Получить количество полученных файлов
     */
    public int getReceivedFilesCount(Long chatId) {
        UserState state = getState(chatId);
        return state != null ? state.getReceivedFiles().size() : 0;
    }

    /**
     * Получить все полученные fileId
     */
    public java.util.List<String> getReceivedFiles(Long chatId) {
        UserState state = getState(chatId);
        return state != null ? state.getReceivedFiles() : java.util.Collections.emptyList();
    }

    /**
     * Получить количество активных пользователей
     */
    public int getActiveUsersCount() {
        return userStates.size();
    }

    /**
     * Очистить все состояния (например, при перезагрузке)
     */
    public void clearAllStates() {
        userStates.clear();
        log.info("Cleared all user states");
    }
}