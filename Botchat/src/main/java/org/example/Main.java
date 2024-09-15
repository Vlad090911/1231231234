package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws TelegramApiException {
        // Ініціалізація API Telegram
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        try {
            // Підключення до бази даних для книг
            Connection booksConn = DriverManager.getConnection("jdbc:sqlite:src/books_library.db");

            // Підключення до бази даних для користувачів
            Connection usersConn = DriverManager.getConnection("jdbc:sqlite:src/users_data.db");

            // Реєстрація бота з обома з'єднаннями
            MyTelegramBot myTelegramBot = new MyTelegramBot(booksConn, usersConn);
            botsApi.registerBot(myTelegramBot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Підключення до бази даних та виконання пошуку
        try (Connection booksConn = DriverManager.getConnection("jdbc:sqlite:src/books_library.db");
             Connection usersConn = DriverManager.getConnection("jdbc:sqlite:src/users_data.db")) {

            DatabaseSearchService searchService = new DatabaseSearchService(booksConn, usersConn);

            String keyword = "Сергей Карелин"; // Приклад пошукового запиту
            List<Book> books = searchService.searchBooks(keyword);

            if (!books.isEmpty()) {
                String formattedMessage = formatBooksMessage(books, 1, 5); // Форматування результатів з розбивкою на сторінки
                System.out.println(formattedMessage); // Виведення результатів
            } else {
                System.out.println("Нічого не знайдено.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Метод для форматування результатів у вигляді повідомлення
    public static String formatBooksMessage(List<Book> books, int pageNumber, int pageSize) {
        StringBuilder response = new StringBuilder();

        int start = (pageNumber - 1) * pageSize;
        int end = Math.min(start + pageSize, books.size());

        for (int i = start; i < end; i++) {
            Book book = books.get(i);
            response.append(book.getTitle()).append("\n");
            response.append("Автор: ").append(book.getAuthors()).append("\n");
            response.append("Серия: ").append(book.getSeriesName()).append(" № ").append(book.getSeriesNumber()).append("\n");
            response.append("Скачать: ").append(book.getDownloadLink()).append("\n\n");
        }

        int totalPages = (int) Math.ceil((double) books.size() / pageSize);
        for (int i = 1; i <= totalPages; i++) {
            if (i == pageNumber) {
                response.append("·").append(i).append("· ");
            } else {
                response.append(i).append(" ");
            }
        }
        response.append("› ").append(totalPages).append(" »");

        return response.toString();
    }
}
