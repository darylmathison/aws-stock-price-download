package com.darylmathison.market.service.impl;

import com.darylmathison.market.service.S3Service;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Implementation of S3Service for interacting with Amazon S3.
 */
@Service
public class S3ServiceImpl implements S3Service {

    private final ObjectFactory<S3Client> s3ClientFactory;

    public S3ServiceImpl(ObjectFactory<S3Client> s3ClientFactory) {
        this.s3ClientFactory = s3ClientFactory;
    }

    /**
     * Fetches a list of strings from an S3 object.
     *
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @return List of strings read from the S3 object
     */
    @Override
    public List<String> fetchList(String bucket, String key) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(getObject(bucket, key))))) {

            return reader.lines().toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch list from S3: " + bucket + "/" + key, e);
        }
    }

    /**
     * Gets an object from S3 as a byte array.
     *
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @return The object content as byte array
     */
    @Override
    public byte[] getObject(String bucket, String key) {
        try (S3Client s3Client = s3ClientFactory.getObject()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get object from S3: " + bucket + "/" + key, e);
        }
    }

    /**
     * Puts an object into S3.
     *
     * @param bucket The S3 bucket name
     * @param key The S3 object key
     * @param data The data to store in S3
     */
    @Override
    public void putObject(String bucket, String key, byte[] data) {
        try (S3Client s3Client = s3ClientFactory.getObject()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.putObject(putObjectRequest,
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to put object to S3: " + bucket + "/" + key, e);
        }
    }
}