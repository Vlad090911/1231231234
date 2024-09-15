package org.example;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseSearchService {

    private final Connection booksConnection;
    private final Connection usersConnection;
    private final Connection userDataConnection;
    private static final String USER_DATA_DB_URL = "jdbc:sqlite:src/users_data.db";

    /**
     * Конструктор для ініціалізації з'єднань з базою даних.
     *
     * @param booksConnection з'єднання з базою даних книг
     * @param usersConnection з'єднання з базою даних користувачів
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public DatabaseSearchService(Connection booksConnection, Connection usersConnection) throws SQLException {
        this.booksConnection = booksConnection;
        this.usersConnection = usersConnection;
        this.userDataConnection = DriverManager.getConnection(USER_DATA_DB_URL);

        // Створення таблиць, якщо вони не існують
        createTables();
    }

    /**
     * Створює таблиці в базі даних, якщо вони не існують.
     */
    private void createTables() {
        String createUsersTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                "id TEXT PRIMARY KEY, " +  // Змінив тип на TEXT для userId
                "chat_id INTEGER, " +
                "followed_by_author TEXT, " +
                "followed_by_series TEXT, " +
                "last_query TEXT," +
                "subscription TEXT" +
                ");";

        try (Statement stmt = userDataConnection.createStatement()) {
            stmt.execute(createUsersTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Шукає книги за ключовими словами.
     *
     * @param query запит для пошуку
     * @return список книг, що відповідають запиту
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public List<Book> searchBooks(String query) throws SQLException {
        String[] keywords = query.split("\\s+"); // Розбиваємо запит на слова

        // Пошук книг за оригінальними ключовими словами
        List<Book> originalResults = searchByKeywords(keywords);

        // Створення варіантів ключових слів з різними регістрами
        String[] caseVariations = generateCaseVariations(keywords);

        // Пошук для варіантів ключових слів
        List<Book> caseVariationResults = searchByKeywords(caseVariations);

        // Об'єднання результатів
        Set<Book> uniqueBooks = new HashSet<>(originalResults);
        uniqueBooks.addAll(caseVariationResults);

        // Фільтрація результатів, щоб повернути тільки ті, де всі ключові слова присутні
        return filterResults(new ArrayList<>(uniqueBooks), keywords);
    }

    /**
     * Пошук книг за ключовими словами.
     *
     * @param keywords масив ключових слів
     * @return список книг, що відповідають ключовим словам
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    private List<Book> searchByKeywords(String[] keywords) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT books.title, authors.name AS author_name, series.name AS series_name, " +
                        "books.book_number, books.backup_file_path " +
                        "FROM books " +
                        "JOIN authors ON books.author_id = authors.id " +
                        "LEFT JOIN series ON books.series_id = series.id " +
                        "WHERE ("
        );

        // Додаємо умови для кожного ключового слова
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) sqlBuilder.append(" OR ");
            String keyword = keywords[i];
            String likeQuery = "%" + keyword + "%";

            // Додаємо умови для ключового слова
            sqlBuilder.append("(LOWER(authors.name) LIKE LOWER(?) OR LOWER(books.title) LIKE LOWER(?) OR LOWER(series.name) LIKE LOWER(?))");
        }
        sqlBuilder.append(")");

        String sql = sqlBuilder.toString();

        try (PreparedStatement pstmt = booksConnection.prepareStatement(sql)) {
            int index = 1;
            for (String keyword : keywords) {
                String likeQuery = "%" + keyword + "%";

                // Заповнюємо параметри для кожного ключового слова
                pstmt.setString(index++, likeQuery); // для authors.name
                pstmt.setString(index++, likeQuery); // для books.title
                pstmt.setString(index++, likeQuery); // для series.name
            }

            ResultSet rs = pstmt.executeQuery();
            return extractBooksFromResultSet(rs);
        }
    }

    /**
     * Генерує варіанти ключових слів з різними регістрами першої літери.
     *
     * @param keywords масив ключових слів
     * @return масив варіантів ключових слів
     */
    private String[] generateCaseVariations(String[] keywords) {
        String[] variations = new String[keywords.length * 2];
        int index = 0;

        for (String keyword : keywords) {
            if (keyword.length() > 0) {
                // Оригінальне слово з великою першою літерою
                String capitalizedKeyword = Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
                variations[index++] = capitalizedKeyword;

                // Оригінальне слово з маленькою першою літерою
                String lowercaseKeyword = Character.toLowerCase(keyword.charAt(0)) + keyword.substring(1);
                variations[index++] = lowercaseKeyword;
            }
        }

        return variations;
    }

    /**
     * Витягує книги з результату запиту.
     *
     * @param rs результат запиту
     * @return список книг
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    private List<Book> extractBooksFromResultSet(ResultSet rs) throws SQLException {
        List<Book> books = new ArrayList<>();
        while (rs.next()) {
            String title = rs.getString("title");
            String author = rs.getString("author_name");
            String seriesName = rs.getString("series_name");
            String bookNumber = rs.getString("book_number");
            String downloadLink = rs.getString("backup_file_path");

            books.add(new Book(title, author, seriesName, bookNumber, downloadLink));
        }
        return books;
    }

    /**
     * Фільтрує результати, щоб повернути тільки ті, де всі ключові слова присутні.
     *
     * @param books    список книг
     * @param keywords масив ключових слів
     * @return відфільтрований список книг
     */
    private List<Book> filterResults(List<Book> books, String[] keywords) {
        List<Book> filteredBooks = new ArrayList<>();

        for (Book book : books) {
            boolean allKeywordsMatch = true;
            for (String keyword : keywords) {
                if (!(book.getTitle().toLowerCase().contains(keyword.toLowerCase()) ||
                        book.getAuthors().toLowerCase().contains(keyword.toLowerCase()) ||
                        (book.getSeriesName() != null && book.getSeriesName().toLowerCase().contains(keyword.toLowerCase())))) {
                    allKeywordsMatch = false;
                    break;
                }
            }
            if (allKeywordsMatch) {
                filteredBooks.add(book);
            }
        }

        return filteredBooks;
    }

    /**
     * Зберігає дані про користувача в базі даних.
     *
     * @param userId ID користувача
     * @param chatId ID чату
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public void saveUser(String userId, long chatId, String followedByAuthor, String followedBySeries, String subscription, String lastQuery) throws SQLException {
        System.out.println("Збереження користувача: ID = " + userId + ", ChatID = " + chatId);

        // SQL для перевірки наявності користувача
        String sqlCheck = "SELECT COUNT(*) FROM users WHERE id = ?";

        try (PreparedStatement pstmtCheck = usersConnection.prepareStatement(sqlCheck)) {
            pstmtCheck.setString(1, userId); // Змінив тип на String
            ResultSet rs = pstmtCheck.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                // Якщо користувач існує, оновлюємо запис
                System.out.println("Користувач з ID " + userId + " знайдено. Оновлення запису.");
                String sqlUpdate = "UPDATE users SET chat_id = ?, followed_by_author = ?, followed_by_series = ?, subscription = ?, last_query = ? WHERE id = ?";

                try (PreparedStatement pstmtUpdate = usersConnection.prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setLong(1, chatId);
                    pstmtUpdate.setString(2, followedByAuthor);
                    pstmtUpdate.setString(3, followedBySeries);
                    pstmtUpdate.setString(4, subscription);
                    pstmtUpdate.setString(5, lastQuery);
                    pstmtUpdate.setString(6, userId); // Змінив тип на String
                    int updatedRows = pstmtUpdate.executeUpdate();
                    System.out.println("Оновлено рядків: " + updatedRows);
                }
            } else {
                // Якщо користувача немає, вставляємо новий запис
                System.out.println("Користувача з ID " + userId + " не знайдено. Додаємо новий запис.");
                String sqlInsert = "INSERT INTO users (id, chat_id, followed_by_author, followed_by_series, subscription, last_query) VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement pstmtInsert = usersConnection.prepareStatement(sqlInsert)) {
                    pstmtInsert.setString(1, userId); // Змінив тип на String
                    pstmtInsert.setLong(2, chatId);
                    pstmtInsert.setString(3, followedByAuthor);
                    pstmtInsert.setString(4, followedBySeries);
                    pstmtInsert.setString(5, subscription);
                    pstmtInsert.setString(6, lastQuery);
                    int insertedRows = pstmtInsert.executeUpdate();
                    System.out.println("Вставлено рядків: " + insertedRows);
                }
            }
        } catch (SQLException e) {
            System.err.println("Помилка при збереженні користувача: " + e.getMessage());
            e.printStackTrace();
            throw e; // Перенаправлення виключення
        }
    }

    /**
     * Оновлює інформацію про те, чи слідкує користувач за автором.
     *
     * @param userId ID користувача
     * @param author ID автора
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public void updateUserFollowedByAuthor(String userId, String author) throws SQLException {
        String sql = "UPDATE users SET followed_by_author = ? WHERE id = ?";
        try (PreparedStatement pstmt = usersConnection.prepareStatement(sql)) {
            pstmt.setString(1, author);
            pstmt.setString(2, userId); // Змінив тип на String
            pstmt.executeUpdate();
        }
    }

    /**
     * Оновлює інформацію про те, чи слідкує користувач за серією.
     *
     * @param userId ID користувача
     * @param series ID серії
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public void updateUserFollowedBySeries(String userId, String series) throws SQLException {
        String sql = "UPDATE users SET followed_by_series = ? WHERE id = ?";
        try (PreparedStatement pstmt = usersConnection.prepareStatement(sql)) {
            pstmt.setString(1, series);
            pstmt.setString(2, userId); // Змінив тип на String
            pstmt.executeUpdate();
        }
    }

    /**
     * Оновлює статус підписки користувача.
     *
     * @param userId        ID користувача
     * @param subscription статус підписки
     * @throws SQLException якщо виникає помилка доступу до бази даних
     */
    public void updateUserSubscription(String userId, String subscription) throws SQLException {
        String sql = "UPDATE users SET subscription = ? WHERE id = ?";
        try (PreparedStatement pstmt = usersConnection.prepareStatement(sql)) {
            pstmt.setString(1, subscription);
            pstmt.setString(2, userId); // Змінив тип на String
            pstmt.executeUpdate();
        }
    }

    public boolean isSubscriptionActive(String userId) throws SQLException {
        String sql = "SELECT subscription FROM users WHERE id = ?";
        try (PreparedStatement pstmt = usersConnection.prepareStatement(sql)) {
            pstmt.setString(1, userId); // Змінив тип на String
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String subscriptionEndDateStr = rs.getString("subscription");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate subscriptionEndDate = (subscriptionEndDateStr != null && !subscriptionEndDateStr.isEmpty()) ?
                        LocalDate.parse(subscriptionEndDateStr, formatter) : null;
                if (subscriptionEndDate != null) {
                    return !subscriptionEndDate.isBefore(LocalDate.now());
                }
            }
            return false; // Якщо підписки немає або дата закінчення невідома
        }
    }

    public User getUser(String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Визначення формату дати

        try (PreparedStatement pstmt = usersConnection.prepareStatement(sql)) {
            pstmt.setString(1, userId); // Змінив тип на String
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Отримання дати закінчення підписки як рядок
                String subscriptionEndDateStr = rs.getString("subscription");

                // Конвертація рядка у LocalDate
                LocalDate subscriptionEndDate = (subscriptionEndDateStr != null && !subscriptionEndDateStr.isEmpty()) ?
                        LocalDate.parse(subscriptionEndDateStr, formatter) : null;

                // Визначення статусу підписки
                String subscriptionStatus = (subscriptionEndDate != null && !LocalDate.now().isAfter(subscriptionEndDate)) ? "Active" : "Expired";

                return new User(
                        rs.getString("id"), // Змінив тип на String
                        rs.getLong("chat_id"),
                        null, // Можливо, це поле не використовується
                        subscriptionEndDate,
                        subscriptionStatus,
                        rs.getString("last_query")
                );
            }
        }
        return null;  // або кинути виняток, якщо користувача не знайдено
    }


    public Book getBookByDownloadLink(String downloadLink) throws SQLException {
        String query = "SELECT b.title, a.name AS author_name, s.name AS series_name, b.book_number, b.backup_file_path  " +
                "FROM books b " +
                "JOIN authors a ON b.author_id = a.id " +
                "LEFT JOIN series s ON b.series_id = s.id " +
                "WHERE b.backup_file_path  = ?";
        try (PreparedStatement stmt = booksConnection.prepareStatement(query)) {
            stmt.setString(1, downloadLink);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    String authors = rs.getString("author_name"); // Це коректний стовпець для авторів
                    String seriesName = rs.getString("series_name");
                    String seriesNumber = rs.getString("book_number");
                    String downloadXPath = rs.getString("backup_file_path");

                    return new Book(title, authors, seriesName, seriesNumber, downloadXPath);
                } else {
                    return null;  // Якщо книга не знайдена
                }
            }
        }
    }

}
