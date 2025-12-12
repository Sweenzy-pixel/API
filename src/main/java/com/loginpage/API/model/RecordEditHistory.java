package com.loginpage.API.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "record_edit_history")
public class RecordEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id")
    private CsvRecordEntity record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "csv_file_id")
    private CsvFile csvFile;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String oldData; // JSON of old values

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String newData; // JSON of new values

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String changedFields; // JSON of which fields changed

    private LocalDateTime editedAt;

    private String editedBy; // Could be username if you have user management

    public RecordEditHistory() {
        this.editedAt = LocalDateTime.now();
    }

    public RecordEditHistory(CsvRecordEntity record, CsvFile csvFile, String oldData, String newData, String changedFields) {
        this.record = record;
        this.csvFile = csvFile;
        this.oldData = oldData;
        this.newData = newData;
        this.changedFields = changedFields;
        this.editedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CsvRecordEntity getRecord() { return record; }
    public void setRecord(CsvRecordEntity record) { this.record = record; }

    public CsvFile getCsvFile() { return csvFile; }
    public void setCsvFile(CsvFile csvFile) { this.csvFile = csvFile; }

    public String getOldData() { return oldData; }
    public void setOldData(String oldData) { this.oldData = oldData; }

    public String getNewData() { return newData; }
    public void setNewData(String newData) { this.newData = newData; }

    public String getChangedFields() { return changedFields; }
    public void setChangedFields(String changedFields) { this.changedFields = changedFields; }

    public LocalDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(LocalDateTime editedAt) { this.editedAt = editedAt; }

    public String getEditedBy() { return editedBy; }
    public void setEditedBy(String editedBy) { this.editedBy = editedBy; }
}

