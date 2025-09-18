package org.example.bot;

import org.example.config.ConfigProperties;
import org.example.model.Task;
import org.example.model.User;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Телеграм бот с помощью которого можно будет добавлять и отслежвать задачи
 */
public class TaskTracker extends TelegramLongPollingBot {
    private final Map<Long, UserStates> userStates = new HashMap<>();
    private final Map<Long, User> userMap = new HashMap<>();
    private final List<String> categories = new ArrayList<>(List.of("Разработка", "Аналитика", "Тестирование"));

    /**
     * Основной метод org.telegram.telegrambots который работает по технологии Long Polling
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String currentText = update.getMessage().getText();
            long currentChatId = update.getMessage().getChatId();

            UserStates currentStates = userStates.getOrDefault(currentChatId, UserStates.AWAITING);

            switch (currentStates) {
                case AWAITING -> handlerMainMenu(currentText, currentChatId);
                case AWAITING_TASK_NAME -> addTaskToList(currentText, currentChatId);
                case AWAITING_TASK_CATEGORY -> addCategoryTask(currentText, currentChatId);
                case AWAITING_TASK_DISC -> addDescription(currentText, currentChatId);
                case AWAITING_TASK_DEADLINES -> addDeadlines(currentText, currentChatId);
                case VIEWING_TASKS -> viewingUpdateTask(currentText, currentChatId);
                case EDITING_TASK -> menuEditingTask(currentText, currentChatId);
                case ADD_NEW_CATEGORY -> addNewCategory(currentText, currentChatId);
            }
        }
    }

    /**
     * Основные команды бота
     *
     */
    private void handlerMainMenu(String currentText, long currentChatId) {
        switch (currentText) {
            case "/start":
                sendWelcome(currentChatId);
                showMainMenu(currentChatId);
                break;
            case "/help":
            case "❓ Помощь":
                sendHelp(currentChatId);
                break;
            case "/menu":
                showMainMenu(currentChatId);
                break;
            case "/add_task":
            case "➕ Добавить задачу":
                createTask(currentChatId);
                break;
            case "/my_tasks":
            case "📋 Мои задачи":
                showCurrentTask(currentChatId);
                break;
            case "/done_task":
            case "✅ Выполненные задачи":
                showDoneTask(currentChatId);
                break;
            default:
                sendMessage("Вот актуальные команды:", currentChatId);
                sendHelp(currentChatId);
        }
    }

    /**
     * Первостепенная регистрация пользователя при создании первой задачи, и далее просто начало добавления задач
     */
    private void createTask(long currentChatId) {
        if (!userMap.containsKey(currentChatId)) {
            userMap.put(currentChatId, new User(currentChatId));
        }

        sendMessage("Дай название своей задаче!", currentChatId);
        userStates.put(currentChatId, UserStates.AWAITING_TASK_NAME);
    }

    private void addTaskToList(String currentText, long currentChatId) {
        userStates.put(currentChatId, UserStates.AWAITING_TASK_CATEGORY);

        userMap.get(currentChatId).getTasks().add(new Task(currentChatId, currentText));
        Task task = userMap.get(currentChatId).getTasks().getLast();
        task.setCompleted(false);

        SendMessage message = SendMessage.builder()
                .chatId(currentChatId)
                .text("Выберите категорию задачи:")
                .replyMarkup(createCategoriesKeyboard(categories))
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void addCategoryTask(String currentText, long currentChatId) {
        userStates.put(currentChatId, UserStates.AWAITING_TASK_DISC);

        if (currentText.equals("➕ Добавить категорию")) {
            userStates.put(currentChatId, UserStates.ADD_NEW_CATEGORY);
            sendMessage("Введите название категории!", currentChatId);
            return;
        }

        Task task = userMap.get(currentChatId).getTasks().getLast();
        task.setCategory(currentText);
        sendMessage("Добавь описание своей задаче:", currentChatId);

    }

    private void addNewCategory(String currentText, long currentChatId) {
        userStates.put(currentChatId, UserStates.AWAITING_TASK_DISC);

        Task task = userMap.get(currentChatId).getTasks().getLast();
        task.setCategory(currentText);
        categories.add(currentText);
        sendMessage("Добавь описание своей задаче:", currentChatId);

    }


    private ReplyKeyboardMarkup createCategoriesKeyboard(List<String> categories) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        for (String category : categories) {
            KeyboardRow row = new KeyboardRow();
            row.add(category);
            keyboard.add(row);
        }

        KeyboardRow cancelRow = new KeyboardRow();
        cancelRow.add("➕ Добавить категорию");
        keyboard.add(cancelRow);


//        KeyboardRow cancelRow = new KeyboardRow(); // TODO Добавить отмену процесса создания задачи
//        cancelRow.add("❌ Отмена");
//        keyboard.add(cancelRow);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void addDescription(String currentText, long currentChatId) {
        Task task = userMap.get(currentChatId).getTasks().getLast();
        task.setDesc(currentText);

        LocalDateTime localDateTime = LocalDateTime.now();
        String formattedDateTime = localDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        sendMessage("Когда задача должна быть выполнена? \n" +
                "(дата должна быть в формате " + formattedDateTime + ")", currentChatId);
        userStates.put(currentChatId, UserStates.AWAITING_TASK_DEADLINES);
    }

    private void addDeadlines(String currentText, long currentChatId) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"); // TODO Добавить валидацию дат
            LocalDateTime localDateTime = LocalDateTime.parse(currentText, formatter);

            Task task = userMap.get(currentChatId).getTasks().getLast();
            task.setDeadline(localDateTime);

            userStates.put(currentChatId, UserStates.AWAITING);

            sendMessage("✅ Задача добавлена!", currentChatId);

            showMainMenu(currentChatId);

        } catch (DateTimeParseException e) {
            sendMessage("Неверный формат даты! Введите в формате ДД/ММ/ГГГГ ЧЧ:ММ:", currentChatId);
        }
    }

    /**
     * Клавиатура основного меню
     */
    private void showMainMenu(Long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();


        KeyboardRow row1 = new KeyboardRow();
        row1.add("➕ Добавить задачу");
        row1.add("📋 Мои задачи");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("✅ Выполненные задачи"); // TODO Добавит флаг выполнения задачи
        row2.add("❓ Помощь");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Выберите действие:")
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Просмотр списка текущих задач
     */
    private void showCurrentTask(long currentChatId) {
        if (!userMap.containsKey(currentChatId) || userMap.get(currentChatId).isNotTask()) {

            sendMessage("У вас нет активных задач!", currentChatId);
            userStates.put(currentChatId, UserStates.AWAITING);
            handlerMainMenu("/menu", currentChatId);
            return;
        }

        userStates.put(currentChatId, UserStates.VIEWING_TASKS);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        for (Task task : userMap.get(currentChatId).getTasks()) {
            if (!task.isCompleted()) {
                KeyboardRow row = new KeyboardRow();
                row.add(task.getName());
                keyboard.add(row);
            }
        }

        KeyboardRow cancelRow = new KeyboardRow();
        cancelRow.add("❌ Отмена");
        keyboard.add(cancelRow);

        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(currentChatId)
                .text("Выберите задачу:")
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Просмотр выполненных задач
     */

    private void showDoneTask(long currentChatId) {

        if (!userMap.containsKey(currentChatId) || !userMap.get(currentChatId).isNotTask()) {

            sendMessage("Вы еще не выполнили ни одной задачи!", currentChatId);
            userStates.put(currentChatId, UserStates.AWAITING);
            handlerMainMenu("/menu", currentChatId);
            return;
        }
        userStates.put(currentChatId, UserStates.AWAITING);

        userMap.get(currentChatId).getTasks().stream().filter(Task::isCompleted).forEach(task -> sendMessage("*" + task.getName()
                + "*" + " из категории " + "*" + task.getCategory() + "*" +
                "\nОписание - " + task.getDesc() + " \n✅ Выполнена!", currentChatId));
    }

    /**
     * Клавиатура управления задачами
     */
    private void viewingUpdateTask(String currentText, long currentChatId) {
        if (currentText.equals("❌ Отмена")) {
            sendMessage("Возврат в меню!", currentChatId);
            userStates.put(currentChatId, UserStates.AWAITING);
            showMainMenu(currentChatId);
            return;
        }

        userStates.put(currentChatId, UserStates.EDITING_TASK);

        LinkedList<Task> tasks = userMap.get(currentChatId).getTasks();

        Task task = tasks.stream()
                .filter(t -> t.getName().equals(currentText))
                .findFirst()
                .orElse(null);

        String status = task.isCompleted() ? "✅ Выполнена!" : "⌛ В процессе!";

        sendMessage("*" + task.getName() + "*" + " из категории " + "*" + task.getCategory() + "*" +
                        "\nОписание - " + task.getDesc() + " Выполнить до: " + "*" +
                        task.getDeadline() + "*" + "\n" + status, currentChatId);
//                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) TODO Вернуть время, убрать лишнее

        if(tasks.remove(task)) {
            tasks.addLast(task);
        }

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow(); // TODO Кастомизация
        row1.add("✅ Выполнить задачу!");
//        row1.add("Удалить задачу!");

        KeyboardRow row2 = new KeyboardRow();
//        row2.add("Изменить задачу!");
        row2.add("⬅ Назад!");

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(currentChatId)
                .text("Что вы хотите сделать с задачей?")
                .replyMarkup(keyboardMarkup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

    /**
     * Меню управления задачами
     */
    private void menuEditingTask(String currentText, long currentChatId) {
        switch (currentText) {
            case "✅ Выполнить задачу!":
                taskDone(currentChatId);
                break;
            case "⬅ Назад!":
                showCurrentTask(currentChatId);
                break;
            default:
        }
    }

    private  void taskDone(long currentChatId) {
        userStates.put(currentChatId, UserStates.VIEWING_TASKS);

        Task task = userMap.get(currentChatId).getTasks().getLast();
        if(task.isCompleted()) {
            sendMessage("✖ Задача уже отмечена как выполнена!", currentChatId);
            viewingUpdateTask(task.getName(), currentChatId);
            return;
        }

        task.setCompleted(true);

        sendMessage("Задача выполнена!", currentChatId);
        showCurrentTask(currentChatId);
    }

    private void sendMessage(String message, long chatId) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(message)
                .parseMode("Markdown")
                .build();

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendHelp(long chatId) {
        String helpText = """
                \uD83D\uDD27 *Доступные команды:*
                */start* - начать работу
                */add_task* - добавить задачу
                */my_tasks* - посмотреть задачи
                */menu* - показать меню
                */help* - доступные команды""";

        sendMessage(helpText, chatId);
    }

    private void sendWelcome(long chatId) {
        String welcomeText = """
                \uD83D\uDC4B *Привет!*\s
                
                Я бот, *планировщик задач!* \
                
                Могу помочь тебе составить список задач и следить за дедлайнами!""";

        sendMessage(welcomeText, chatId);
    }

    @Override
    public String getBotUsername() {
        return ConfigProperties.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return ConfigProperties.getBotToken();
    }
}
