package org.example;

import java.util.Objects;

public class Book {
    private final String title;
    private final String authors;
    private final String seriesName;
    private final String seriesNumber;
    private final String downloadLink;

    public Book(String title, String authors, String seriesName, String seriesNumber, String downloadLink) {
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
        if (authors == null || authors.isEmpty()) {
            throw new IllegalArgumentException("Authors cannot be null or empty");
        }
        // Optional fields can be left without validation if they are allowed to be null
        this.title = title;
        this.authors = authors;
        this.seriesName = seriesName;
        this.seriesNumber = seriesNumber;
        this.downloadLink = downloadLink;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthors() {
        return authors;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public String getSeriesNumber() {
        return seriesNumber;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(title, book.title) &&
                Objects.equals(authors, book.authors) &&
                Objects.equals(seriesName, book.seriesName) &&
                Objects.equals(seriesNumber, book.seriesNumber) &&
                Objects.equals(downloadLink, book.downloadLink);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, authors, seriesName, seriesNumber, downloadLink);
    }

    @Override
    public String toString() {
        return "Book{" +
                "title='" + title + '\'' +
                ", authors='" + authors + '\'' +
                ", seriesName='" + seriesName + '\'' +
                ", seriesNumber='" + seriesNumber + '\'' +
                ", downloadLink='" + downloadLink + '\'' +
                '}';
    }
}
