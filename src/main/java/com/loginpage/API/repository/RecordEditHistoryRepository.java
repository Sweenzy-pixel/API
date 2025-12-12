package com.loginpage.API.repository;

import com.loginpage.API.model.RecordEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordEditHistoryRepository extends JpaRepository<RecordEditHistory, Long> {
    List<RecordEditHistory> findByCsvFileIdOrderByEditedAtDesc(Long csvFileId);
    List<RecordEditHistory> findByRecordIdOrderByEditedAtDesc(Long recordId);
    
    @Modifying
    @Query("DELETE FROM RecordEditHistory h WHERE h.csvFile.id = :csvFileId")
    void deleteByCsvFileId(@Param("csvFileId") Long csvFileId);
}

