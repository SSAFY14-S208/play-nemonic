package com.nemonicworld.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nemonicworld.common.exception.FileStorageException;
import com.nemonicworld.files.config.MinioStorageProperties;
import com.nemonicworld.relay.service.cleanup.MinioRelayTempFileStorage;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MinioRelayTempFileStorageTest {

    private static final String BUCKET = "nemonic-local";

    private MinioClient minioClient;
    private MinioRelayTempFileStorage storage;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        MinioStorageProperties properties = new MinioStorageProperties("http://localhost:9000", "http://localhost:9000",
            "minioadmin", "minioadmin", BUCKET, 10, 10 * 1024 * 1024);
        storage = new MinioRelayTempFileStorage(minioClient, properties);
    }

    @Test
    void deleteObjectsRemovesRelayTempObjects() throws Exception {
        storage.deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png"));

        ArgumentCaptor<RemoveObjectArgs> argsCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(argsCaptor.capture());
        assertThat(argsCaptor.getAllValues()).extracting(RemoveObjectArgs::bucket).containsOnly(BUCKET);
        assertThat(argsCaptor.getAllValues()).extracting(RemoveObjectArgs::object)
            .containsExactly("relay/tmp/AB3K9Q/0/face.png", "relay/tmp/AB3K9Q/0/face-hint.png");
    }

    @Test
    void deleteObjectsTreatsMissingObjectAsSuccess() throws Exception {
        doThrow(errorResponseException("NoSuchKey")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatCode(() -> storage.deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png"))).doesNotThrowAnyException();
    }

    @Test
    void deleteObjectsRejectsNonRelayTempObjectKeyBeforeMinioCall() {
        assertThatThrownBy(() -> storage.deleteObjects(List.of("relay/results/artifact-id/original.png")))
            .isInstanceOf(FileStorageException.class);

        try {
            verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void deleteObjectsWrapsMinioDeleteFailure() throws Exception {
        doThrow(errorResponseException("AccessDenied")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> storage.deleteObjects(List.of("relay/tmp/AB3K9Q/0/face.png")))
            .isInstanceOf(FileStorageException.class);
    }

    @Test
    void findOldTempObjectKeysListsOnlyOldRelayTempObjects() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 6, 16, 0);
        Item oldTempItem = item("relay/tmp/AB3K9Q/0/face.png", cutoff.minusMinutes(1), false);
        Item recentTempItem = item("relay/tmp/AB3K9Q/0/body.png", cutoff.plusSeconds(1), false);
        Item oldResultItem = item("relay/results/artifact-id/original.png", cutoff.minusDays(1), false);
        Item directoryItem = item("relay/tmp/AB3K9Q/0/", cutoff.minusDays(1), true);
        given(minioClient.listObjects(any(ListObjectsArgs.class))).willReturn(List.of(new Result<>(oldTempItem),
            new Result<>(recentTempItem), new Result<>(oldResultItem), new Result<>(directoryItem)));

        List<String> objectKeys = storage.findOldTempObjectKeys(cutoff, 1000);

        assertThat(objectKeys).containsExactly("relay/tmp/AB3K9Q/0/face.png");
        ArgumentCaptor<ListObjectsArgs> argsCaptor = ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(minioClient).listObjects(argsCaptor.capture());
        assertThat(argsCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(argsCaptor.getValue().prefix()).isEqualTo("relay/tmp/");
        assertThat(argsCaptor.getValue().recursive()).isTrue();
    }

    @Test
    void findOldTempObjectKeysHonorsLimit() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 6, 16, 0);
        Item firstItem = item("relay/tmp/AB3K9Q/0/face.png", cutoff.minusMinutes(1), false);
        Item secondItem = item("relay/tmp/AB3K9Q/0/body.png", cutoff.minusMinutes(1), false);
        given(minioClient.listObjects(any(ListObjectsArgs.class)))
            .willReturn(List.of(new Result<>(firstItem), new Result<>(secondItem)));

        List<String> objectKeys = storage.findOldTempObjectKeys(cutoff, 1);

        assertThat(objectKeys).containsExactly("relay/tmp/AB3K9Q/0/face.png");
    }

    @Test
    void findOldTempObjectKeysWrapsListFailure() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 6, 16, 0);
        given(minioClient.listObjects(any(ListObjectsArgs.class)))
            .willReturn(List.of(new Result<Item>(new IOException("boom"))));

        assertThatThrownBy(() -> storage.findOldTempObjectKeys(cutoff, 1000)).isInstanceOf(FileStorageException.class);
    }

    private ErrorResponseException errorResponseException(String code) {
        ErrorResponse errorResponse = new ErrorResponse(code, code, BUCKET, "relay/tmp/AB3K9Q/0/face.png", null,
            "request-id", "host-id");

        return new ErrorResponseException(errorResponse, null, "http://localhost:9000");
    }

    private Item item(String objectName, LocalDateTime lastModified, boolean directory) {
        Item item = mock(Item.class);
        given(item.objectName()).willReturn(objectName);
        given(item.lastModified()).willReturn(ZonedDateTime.of(lastModified, ZoneId.systemDefault()));
        given(item.isDir()).willReturn(directory);

        return item;
    }
}
