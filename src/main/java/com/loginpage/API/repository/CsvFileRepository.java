package com.loginpage.API.repository;

import com.loginpage.API.model.CsvFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CsvFileRepository extends JpaRepository<CsvFile, Long> {
    CsvFile findTopByOrderByUploadedAtDesc();
    java.util.List<CsvFile> findAllByOrderByUploadedAtDesc();
}
