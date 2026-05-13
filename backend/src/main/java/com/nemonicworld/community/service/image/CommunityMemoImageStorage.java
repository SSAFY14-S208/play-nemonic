package com.nemonicworld.community.service.image;

public interface CommunityMemoImageStorage {

    byte[] download(String objectKey);

    void upload(String objectKey, byte[] bytes, String contentType);

    void deleteQuietly(String objectKey);
}
