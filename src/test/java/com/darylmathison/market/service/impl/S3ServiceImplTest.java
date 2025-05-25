package com.darylmathison.market.service.impl;

import com.darylmathison.market.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class S3ServiceImplTest {

    @Mock
    private ObjectFactory<S3Client> s3ClientFactory;

    @Mock
    private S3Client s3Client;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        when(s3ClientFactory.getObject()).thenReturn(s3Client);
        s3Service = new S3ServiceImpl(s3ClientFactory);
    }

    @Test
    void fetchList_shouldReturnLinesList() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";
        String content = "line1\nline2\nline3";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        // Create a spy to partially mock the s3Service
        S3Service s3ServiceSpy = spy(s3Service);
        doReturn(contentBytes).when(s3ServiceSpy).getObject(bucket, key);

        // When
        List<String> result = s3ServiceSpy.fetchList(bucket, key);

        // Then
        assertEquals(3, result.size());
        assertEquals("line1", result.get(0));
        assertEquals("line2", result.get(1));
        assertEquals("line3", result.get(2));
        verify(s3ServiceSpy).getObject(bucket, key);
    }

    @Test
    void fetchList_shouldThrowRuntimeException_whenGetObjectFails() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";

        // Create a spy to partially mock the s3Service
        S3Service s3ServiceSpy = spy(s3Service);
        doThrow(new RuntimeException("S3 error")).when(s3ServiceSpy).getObject(bucket, key);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> s3ServiceSpy.fetchList(bucket, key));
        assertTrue(exception.getMessage().contains("Failed to fetch list from S3"));
        verify(s3ServiceSpy).getObject(bucket, key);
    }

    @Test
    void getObject_shouldReturnByteArray() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";
        byte[] expectedBytes = "test content".getBytes(StandardCharsets.UTF_8);

        // Mock S3Client responses
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(expectedBytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        // When
        byte[] result = s3Service.getObject(bucket, key);

        // Then
        assertArrayEquals(expectedBytes, result);
        verify(s3ClientFactory).getObject();
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void getObject_shouldThrowRuntimeException_whenS3ClientFails() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(new RuntimeException("S3 error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> s3Service.getObject(bucket, key));
        assertTrue(exception.getMessage().contains("Failed to get object from S3"));
        verify(s3ClientFactory).getObject();
    }

    @Test
    void putObject_shouldPutObjectSuccessfully() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";
        byte[] data = "test content".getBytes(StandardCharsets.UTF_8);

        // Mock necessary behavior
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
          return null;
        });

        // When
        s3Service.putObject(bucket, key, data);

        // Then
        verify(s3ClientFactory).getObject();
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void putObject_shouldThrowRuntimeException_whenS3ClientFails() {
        // Given
        String bucket = "test-bucket";
        String key = "test-file.txt";
        byte[] data = "test content".getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException("S3 error")).when(s3Client)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> s3Service.putObject(bucket, key, data));
        assertTrue(exception.getMessage().contains("Failed to put object to S3"));
        verify(s3ClientFactory).getObject();
    }
}