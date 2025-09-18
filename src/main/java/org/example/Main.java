package org.example;

import org.example.bot.TaskTracker;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Создаем экземпляр Telegram Bots API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Регистрируем нашего бота
            botsApi.registerBot(new TaskTracker());
            System.out.println("Бот запущен и готов к работе!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }


    }
}