package com.nemonicworld.files.service;

import com.nemonicworld.files.dto.request.FilePresignRequest;
import com.nemonicworld.files.dto.response.FileConfirmResponse;
import com.nemonicworld.files.dto.response.FileDeleteResponse;
import com.nemonicworld.files.dto.response.FilePresignResponse;
import com.nemonicworld.files.dto.response.FileViewUrlResponse;
import com.nemonicworld.files.service.upload.FileUploadLifecycleUseCase;
import com.nemonicworld.files.service.upload.FileUploadPresignUseCase;
import com.nemonicworld.files.service.view.FileViewUrlUseCase;
import org.springframework.stereotype.Service;

/**
 * 파일 업로드 URL 발급 흐름을 처리하는 서비스 구현체입니다.
 *
 * <p>
 * 실제 파일 바이너리는 백엔드를 거치지 않고 클라이언트가 MinIO에 직접 PUT 업로드합니다. 이 서비스는 사용자와 요청값을 검증하고,
 * 이후 confirm/delete API에서 추적할 수 있도록 file_upload pending 메타데이터를 먼저 저장합니다.
 */
@Service
public class FileServiceImpl implements FileService {

    private final FileUploadPresignUseCase fileUploadPresignUseCase;
    private final FileViewUrlUseCase fileViewUrlUseCase;
    private final FileUploadLifecycleUseCase fileUploadLifecycleUseCase;

    public FileServiceImpl(FileUploadPresignUseCase fileUploadPresignUseCase, FileViewUrlUseCase fileViewUrlUseCase,
        FileUploadLifecycleUseCase fileUploadLifecycleUseCase) {
        this.fileUploadPresignUseCase = fileUploadPresignUseCase;
        this.fileViewUrlUseCase = fileViewUrlUseCase;
        this.fileUploadLifecycleUseCase = fileUploadLifecycleUseCase;
    }

    /**
     * 업로드 가능한 사용자와 파일 요청인지 검증한 뒤, DB에 pending 업로드 기록을 남기고 임시 PUT URL을 반환합니다.
     */
    @Override
    public FilePresignResponse createPresignedUrl(String userUuidValue, FilePresignRequest request) {
        return fileUploadPresignUseCase.createPresignedUrl(userUuidValue, request);
    }

    /**
     * 업로드 완료된 비공개 파일을 브라우저에서 잠깐 조회할 수 있는 GET URL을 반환합니다.
     */
    @Override
    public FileViewUrlResponse createViewUrl(String userUuidValue, String fileIdValue) {
        return fileViewUrlUseCase.createViewUrl(userUuidValue, fileIdValue);
    }

    /**
     * MinIO에 실제 객체가 업로드되었는지 확인하고 pending 업로드를 uploaded 상태로 확정합니다.
     */
    @Override
    public FileConfirmResponse confirmUpload(String userUuidValue, String fileIdValue) {
        return fileUploadLifecycleUseCase.confirmUpload(userUuidValue, fileIdValue);
    }

    /**
     * 대기 상태 업로드를 취소하고 MinIO 객체와 DB 메타데이터를 삭제 상태로 정리합니다.
     */
    @Override
    public FileDeleteResponse deleteUpload(String userUuidValue, String fileIdValue) {
        return fileUploadLifecycleUseCase.deleteUpload(userUuidValue, fileIdValue);
    }
}
