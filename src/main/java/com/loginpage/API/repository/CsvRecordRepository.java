package com.loginpage.API.repository;

import com.loginpage.API.model.CsvRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CsvRecordRepository extends JpaRepository<CsvRecordEntity, Long> {
    List<CsvRecordEntity> findAllByCsvFileIdOrderByRowNumberAsc(Long csvFileId);
    
    long countByCsvFileIdAndSuccessTrue(Long csvFileId);
    
    long countByCsvFileIdAndSuccessFalse(Long csvFileId);
    
    long countByCsvFileId(Long csvFileId);
    
    void deleteByCsvFileId(Long csvFileId);
}
