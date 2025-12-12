package com.loginpage.API.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "csv_records")
public class CsvRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "csv_file_id")
    private CsvFile csvFile;

    private Integer rowNumber;

    private boolean success;

    private LocalDateTime createdAt;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String data; // JSON representation of the row (map of header->value)

    public CsvRecordEntity() {}

    public CsvRecordEntity(CsvFile csvFile, Integer rowNumber, boolean success, String data) {
        this.csvFile = csvFile;
        this.rowNumber = rowNumber;
        this.success = success;
        this.data = data;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CsvFile getCsvFile() { return csvFile; }
    public void setCsvFile(CsvFile csvFile) { this.csvFile = csvFile; }

    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
