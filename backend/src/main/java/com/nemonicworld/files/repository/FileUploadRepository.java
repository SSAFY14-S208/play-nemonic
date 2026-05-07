package com.nemonicworld.files.repository;

import com.nemonicworld.files.entity.FileUpload;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {
}
